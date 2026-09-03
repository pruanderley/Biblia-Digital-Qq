package biblia.digital.qq;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.view.View;
import com.getcapacitor.BridgeActivity;
import org.json.JSONArray;
import java.util.Locale;

public class MainActivity extends BridgeActivity {

    private TextToSpeech tts;
    private WebView webView;
    private boolean ttsReady = false;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = getBridge().getWebView();
        webView.addJavascriptInterface(new TTSBridge(), "Android");

        // Configurações do WebView
        WebSettings ws = webView.getSettings();
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setSaveFormData(true);
        ws.setDatabaseEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setJavaScriptEnabled(true);

        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_YES);
        }

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

    public class TTSBridge {

        // ── TTS: capítulo inteiro ──────────────────────────────
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
                        tts.speak(verses.getString(i), TextToSpeech.QUEUE_ADD, null, uid);
                    }
                } catch (Exception e) { e.printStackTrace(); }
            });
        }

        // ── TTS: verso único ──────────────────────────────────
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

        // ── Status Bar do Sistema ─────────────────────────────
        @JavascriptInterface
        public void setStatusBarColor(String color) {
            runOnUiThread(() -> {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        int cor = Color.parseColor(color);
                        getWindow().setStatusBarColor(cor);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            int flags = getWindow().getDecorView().getSystemUiVisibility();
                            double lum = (0.299 * Color.red(cor) + 0.587 * Color.green(cor) + 0.114 * Color.blue(cor)) / 255.0;
                            if (lum > 0.55)
                                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                            else
                                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                            getWindow().getDecorView().setSystemUiVisibility(flags);
                        }
                    }
                } catch (Exception e) { e.printStackTrace(); }
            });
        }

        // ── WakeLock ──────────────────────────────────────────
        @JavascriptInterface
        public void acquireWakeLock() { acquireWakeLockInternal(); }

        @JavascriptInterface
        public void releaseWakeLock() { releaseWakeLockInternal(); }

        // ── Outros ────────────────────────────────────────────
        @JavascriptInterface
        public void exitApp() {
            runOnUiThread(() -> { finishAffinity(); System.exit(0); });
        }
    }

    private void acquireWakeLockInternal() {
        try {
            if (wakeLock == null) {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BibliaQQ:WakeLock");
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
        super.onDestroy();
    }
}
