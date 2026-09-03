package biblia.digital.qq;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebSettings;
import com.getcapacitor.BridgeActivity;
import org.json.JSONArray;
import java.util.Locale;

public class MainActivity extends BridgeActivity {

    private TextToSpeech tts;
    private WebView webView;
    private boolean ttsReady = false;
    private PowerManager.WakeLock wakeLock;

    private AudioForegroundService audioService;
    private boolean serviceBound = false;

    private final ServiceConnection serviceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            audioService = ((AudioForegroundService.LocalBinder) binder).get();
            serviceBound = true;
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            audioService = null;
            serviceBound = false;
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = getBridge().getWebView();
        webView.addJavascriptInterface(new TTSBridge(), "Android");

        // ── Configurações do WebView para sugestões de palavras e acentos ──
        WebSettings ws = webView.getSettings();
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setSaveFormData(true);
        ws.setDatabaseEnabled(true);
        ws.setDomStorageEnabled(true);
        // Permite que o WebView aceite input de teclados com sugestão/autocomplete
        ws.setJavaScriptEnabled(true);

        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_YES);
        }

        // Habilita spell checker nativo do Android no WebView (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                // Força o WebView a usar o verificador ortográfico do sistema
                android.view.textservice.TextServicesManager tsm =
                    (android.view.textservice.TextServicesManager) getSystemService(TEXT_SERVICES_MANAGER_SERVICE);
                if (tsm != null && tsm.isSpellCheckerEnabled()) {
                    // O spell checker está habilitado no sistema — o WebView vai usá-lo
                    android.util.Log.d("BibliaHarpa", "Spell checker do sistema: ativo");
                }
            } catch (Exception e) {
                android.util.Log.d("BibliaDigital", "Spell checker não disponível: " + e.getMessage());
            }
        }

        // Callback do AudioForegroundService → JS
        AudioForegroundService.callback = new AudioForegroundService.Callback() {
            @Override
            public void onAudioEnded() {
                runOnUiThread(() -> webView.evaluateJavascript(
                    "if(typeof window.onHarpaEnded==='function') window.onHarpaEnded();", null));
            }
            @Override
            public void onAudioError() {
                runOnUiThread(() -> webView.evaluateJavascript(
                    "if(typeof window.onHarpaError==='function') window.onHarpaError();", null));
            }
        };

        // TTS
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int r = tts.setLanguage(new Locale("pt", "BR"));
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED)
                    tts.setLanguage(Locale.getDefault());

                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String uid) {}
                    @Override
                    public void onDone(String uid) {
                        if ("BATCH_DONE".equals(uid)) {
                            runOnUiThread(() -> webView.evaluateJavascript(
                                "if(typeof window.onTTSChapterDone==='function') window.onTTSChapterDone();", null));
                        } else {
                            runOnUiThread(() -> webView.evaluateJavascript(
                                "if(typeof window.onTTSVerseFinished==='function') window.onTTSVerseFinished();", null));
                        }
                    }
                    @Override
                    public void onError(String uid) {
                        runOnUiThread(() -> webView.evaluateJavascript(
                            "if(typeof window.onTTSVerseFinished==='function') window.onTTSVerseFinished();", null));
                    }
                });
                ttsReady = true;
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        Intent intent = new Intent(this, AudioForegroundService.class);
        bindService(intent, serviceConn, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (serviceBound) { unbindService(serviceConn); serviceBound = false; }
    }

    public class TTSBridge {

        // ── Harpa: player nativo via ForegroundService ────────
        @JavascriptInterface
        public void playHarpaUrl(String url, String title, String info) {
            runOnUiThread(() -> {
                Intent intent = new Intent(MainActivity.this, AudioForegroundService.class);
                intent.setAction(AudioForegroundService.ACTION_PLAY);
                intent.putExtra("url",   url);
                intent.putExtra("title", title != null ? title : "Harpa Cristã");
                intent.putExtra("info",  info  != null ? info  : "Bíblia Harpa Offline");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    startForegroundService(intent);
                else
                    startService(intent);
                acquireWakeLockInternal();
            });
        }

        @JavascriptInterface
        public void playHarpaBytes(String base64, String title, String info) {
            runOnUiThread(() -> {
                try {
                    byte[] data = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
                    java.io.File f = new java.io.File(getCacheDir(), "harpa_current.mp3");
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(f)) {
                        fos.write(data);
                    }
                    Intent intent = new Intent(MainActivity.this, AudioForegroundService.class);
                    intent.setAction(AudioForegroundService.ACTION_PLAY);
                    intent.putExtra("url", f.getAbsolutePath());
                    intent.putExtra("title", title != null ? title : "Bíblia Digital QQ");
                    intent.putExtra("info",  info  != null ? info  : "Bíblia Digital QQ");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        startForegroundService(intent);
                    else
                        startService(intent);
                    acquireWakeLockInternal();
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> webView.evaluateJavascript(
                        "if(typeof window.onHarpaError==='function') window.onHarpaError();", null));
                }
            });
        }

        @JavascriptInterface
        public void requestIgnoreBatteryOptimizations() {
            runOnUiThread(() -> {
                try {
                    android.os.PowerManager pm =
                        (android.os.PowerManager) getSystemService(POWER_SERVICE);
                    String pkg = getPackageName();
                    if (pm != null && !pm.isIgnoringBatteryOptimizations(pkg)) {
                        Intent intent = new Intent(
                            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        intent.setData(Uri.parse("package:" + pkg));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    }
                } catch (Exception e) { e.printStackTrace(); }
            });
        }

        @JavascriptInterface
        public void pauseHarpa() {
            runOnUiThread(() -> {
                Intent i = new Intent(MainActivity.this, AudioForegroundService.class);
                i.setAction(AudioForegroundService.ACTION_PAUSE);
                startService(i);
            });
        }

        @JavascriptInterface
        public void resumeHarpa() {
            runOnUiThread(() -> {
                Intent i = new Intent(MainActivity.this, AudioForegroundService.class);
                i.setAction(AudioForegroundService.ACTION_RESUME);
                startService(i);
            });
        }

        @JavascriptInterface
        public void stopHarpa() {
            runOnUiThread(() -> {
                Intent i = new Intent(MainActivity.this, AudioForegroundService.class);
                i.setAction(AudioForegroundService.ACTION_STOP);
                startService(i);
                releaseWakeLockInternal();
            });
        }

        @JavascriptInterface
        public boolean hasNativePlayer() { return true; }

        // ── Bíblia: notificação durante leitura ───────────────
        @JavascriptInterface
        public void startReadingForeground(String title, String info) {
            runOnUiThread(() -> {
                Intent intent = new Intent(MainActivity.this, AudioForegroundService.class);
                intent.setAction(AudioForegroundService.ACTION_START_READING);
                intent.putExtra("title", title != null ? title : "Bíblia Digital QQ");
                intent.putExtra("info",  info  != null ? info  : "Leitura em voz");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    startForegroundService(intent);
                else
                    startService(intent);
                acquireWakeLockInternal();
            });
        }

        @JavascriptInterface
        public void stopReadingForeground() {
            runOnUiThread(() -> {
                Intent i = new Intent(MainActivity.this, AudioForegroundService.class);
                i.setAction(AudioForegroundService.ACTION_STOP_READING);
                startService(i);
            });
        }

        // ── TTS: capítulo inteiro de uma vez ──────────────────
        @JavascriptInterface
        public void startTTSBatch(String versesJson, float rate, float pitch) {
            if (!ttsReady || tts == null) return;
            acquireWakeLockInternal();
            runOnUiThread(() -> {
                try {
                    tts.setSpeechRate(rate > 0 ? rate : 0.9f);
                    tts.setPitch(pitch > 0 ? pitch : 1.0f);
                    tts.stop();
                    JSONArray verses = new JSONArray(versesJson);
                    for (int i = 0; i < verses.length(); i++) {
                        String uid = (i == verses.length() - 1) ? "BATCH_DONE" : "v" + i;
                        tts.speak(verses.getString(i), TextToSpeech.QUEUE_ADD, new Bundle(), uid);
                    }
                } catch (Exception e) { e.printStackTrace(); }
            });
        }

        // ── TTS: verso único (compatibilidade) ────────────────
        @JavascriptInterface
        public void startTTS(String text, String lang, float rate, float pitch) {
            if (!ttsReady || tts == null) return;
            tts.setSpeechRate(rate > 0 ? rate : 0.9f);
            tts.setPitch(pitch > 0 ? pitch : 1.0f);
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "verse");
        }

        @JavascriptInterface
        public void stopTTS() {
            if (tts != null) tts.stop();
            releaseWakeLockInternal();
        }

        // ── WakeLock ──────────────────────────────────────────
        @JavascriptInterface
        public void acquireWakeLock() { acquireWakeLockInternal(); }

        @JavascriptInterface
        public void releaseWakeLock() { releaseWakeLockInternal(); }

        // ── Outros ────────────────────────────────────────────
        @JavascriptInterface
        public void setStatusBarColor(String color) {
            runOnUiThread(() -> {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        int cor = android.graphics.Color.parseColor(color);
                        getWindow().setStatusBarColor(cor);

                        // Ícones claros ou escuros conforme o fundo
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            int flags = getWindow().getDecorView().getSystemUiVisibility();
                            // Calcula luminância
                            double lum = (0.299 * android.graphics.Color.red(cor)
                                        + 0.587 * android.graphics.Color.green(cor)
                                        + 0.114 * android.graphics.Color.blue(cor)) / 255.0;
                            if (lum > 0.55) {
                                // Fundo claro → ícones escuros
                                flags |= android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                            } else {
                                // Fundo escuro → ícones claros
                                flags &= ~android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                            }
                            getWindow().getDecorView().setSystemUiVisibility(flags);
                        }
                    }
                } catch (Exception e) { e.printStackTrace(); }
            });
        }

        @JavascriptInterface
        public void exitApp() {
            runOnUiThread(() -> { finishAffinity(); System.exit(0); });
        }

        @JavascriptInterface
        public void openUrl(String url) {
            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception e) { e.printStackTrace(); }
            });
        }
    }

    private void acquireWakeLockInternal() {
        try {
            if (wakeLock == null) {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BibliaHarpa:WakeLock");
                wakeLock.setReferenceCounted(false);
            }
            if (!wakeLock.isHeld()) wakeLock.acquire(7200000L);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void releaseWakeLockInternal() {
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); }
        catch (Exception e) {}
    }

    @Override
    public void onBackPressed() {
        getBridge().getWebView().evaluateJavascript(
            "(function(){return typeof window.handleBack==='function'?window.handleBack():false;})()",
            value -> {
                if ("false".equals(value))
                    runOnUiThread(() -> { finishAffinity(); System.exit(0); });
            });
    }

    @Override
    public void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        releaseWakeLockInternal();
        AudioForegroundService.callback = null;
        super.onDestroy();
    }
}
