package biblia.harpa.offline;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import androidx.core.app.NotificationCompat;

public class AudioForegroundService extends Service {

    private static final String CHANNEL_ID = "BibliaHarpa_Audio";
    private static final int    NOTIF_ID   = 1001;

    public static final String ACTION_PLAY          = "PLAY";
    public static final String ACTION_PAUSE         = "PAUSE";
    public static final String ACTION_RESUME        = "RESUME";
    public static final String ACTION_STOP          = "STOP";
    public static final String ACTION_START_READING = "START_READING";
    public static final String ACTION_STOP_READING  = "STOP_READING";

    public interface Callback {
        void onAudioEnded();
        void onAudioError();
    }
    public static Callback callback = null;

    private MediaPlayer mediaPlayer;
    private String currentTitle = "Bíblia Harpa Offline";
    private String currentInfo  = "";

    private final IBinder binder = new LocalBinder();
    public class LocalBinder extends Binder {
        public AudioForegroundService get() { return AudioForegroundService.this; }
    }

    @Override public IBinder onBind(Intent intent) { return binder; }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            showNotification("⏸ " + currentTitle, currentInfo);
            return START_STICKY;
        }
        String action = intent.getAction();
        if (action == null) return START_STICKY;

        switch (action) {
            case ACTION_PLAY:
                currentTitle = intent.getStringExtra("title");
                currentInfo  = intent.getStringExtra("info");
                if (currentTitle == null) currentTitle = "Harpa Cristã";
                if (currentInfo  == null) currentInfo  = "";
                showNotification("⏳ " + currentTitle, currentInfo);
                String url = intent.getStringExtra("url");
                if (url != null) playUrl(url);
                break;
            case ACTION_PAUSE:
                pausePlayer();
                showNotification("⏸ " + currentTitle, currentInfo);
                break;
            case ACTION_RESUME:
                resumePlayer();
                showNotification("▶ " + currentTitle, currentInfo);
                break;
            case ACTION_STOP:
                stopPlayer();
                stopForeground(true);
                stopSelf();
                break;
            case ACTION_START_READING:
                currentTitle = intent.getStringExtra("title");
                currentInfo  = intent.getStringExtra("info");
                if (currentTitle == null) currentTitle = "Bíblia Harpa Offline";
                if (currentInfo  == null) currentInfo  = "Leitura em voz";
                showNotification("📖 " + currentTitle, currentInfo);
                break;
            case ACTION_STOP_READING:
                stopForeground(true);
                stopSelf();
                break;
        }
        return START_STICKY;
    }

    private void playUrl(String url) {
        try {
            if (mediaPlayer != null) { mediaPlayer.release(); mediaPlayer = null; }
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(
                new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build());
            mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
            mediaPlayer.setDataSource(url);
            mediaPlayer.setOnPreparedListener(mp -> {
                mp.start();
                showNotification("▶ " + currentTitle, currentInfo);
            });
            mediaPlayer.setOnCompletionListener(mp -> {
                showNotification("⏹ " + currentTitle, currentInfo);
                if (callback != null) callback.onAudioEnded();
            });
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                if (callback != null) callback.onAudioError();
                return true;
            });
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            e.printStackTrace();
            if (callback != null) callback.onAudioError();
        }
    }

    private void pausePlayer() {
        try { if (mediaPlayer != null && mediaPlayer.isPlaying()) mediaPlayer.pause(); }
        catch (Exception e) {}
    }

    private void resumePlayer() {
        try { if (mediaPlayer != null && !mediaPlayer.isPlaying()) mediaPlayer.start(); }
        catch (Exception e) {}
    }

    private void stopPlayer() {
        try {
            if (mediaPlayer != null) { mediaPlayer.stop(); mediaPlayer.release(); mediaPlayer = null; }
        } catch (Exception e) {}
    }

    private boolean isPlaying() {
        try { return mediaPlayer != null && mediaPlayer.isPlaying(); }
        catch (Exception e) { return false; }
    }

    private void showNotification(String title, String text) {
        Intent tapIntent = new Intent(this, MainActivity.class);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int piFlags = Build.VERSION.SDK_INT >= 23
            ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent tapPi    = PendingIntent.getActivity(this, 0, tapIntent, piFlags);

        Intent pauseIntent = new Intent(this, AudioForegroundService.class);
        pauseIntent.setAction(isPlaying() ? ACTION_PAUSE : ACTION_RESUME);
        PendingIntent pausePi  = PendingIntent.getService(this, 1, pauseIntent, piFlags);

        Intent stopIntent = new Intent(this, AudioForegroundService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPi   = PendingIntent.getService(this, 2, stopIntent, piFlags);

        Notification notif = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title != null ? title : "Bíblia Harpa Offline")
            .setContentText(text  != null ? text  : "")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(tapPi)
            .addAction(android.R.drawable.ic_media_pause,
                isPlaying() ? "Pausar" : "Retomar", pausePi)
            .addAction(android.R.drawable.ic_delete, "Parar", stopPi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build();

        startForeground(NOTIF_ID, notif);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Reprodução de Áudio", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Bíblia Harpa Offline");
            ch.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    @Override
    public void onDestroy() { stopPlayer(); super.onDestroy(); }
}
