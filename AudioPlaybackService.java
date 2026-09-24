package com.soundsplit.prototype;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioRouting;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

public class AudioPlaybackService extends Service {

    public static final String ACTION_PLAY = "com.soundsplit.prototype.PLAY";
    public static final String ACTION_STOP = "com.soundsplit.prototype.STOP";
    public static final String EXTRA_URI = "uri";
    public static final String EXTRA_DEVICE_ID = "device_id";
    public static final String EXTRA_DEVICE_LABEL = "device_label";

    public static final String PREFS = "soundsplit_state";
    public static final String KEY_PLAYING = "playing";
    public static final String KEY_REQUESTED_ROUTE = "requested_route";
    public static final String KEY_ACTUAL_ROUTE = "actual_route";
    public static final String KEY_ROUTE_ACCEPTED = "route_accepted";
    public static final String KEY_LAST_EVENT = "last_event";

    private static final String CHANNEL_ID = "soundsplit_playback";
    private static final int NOTIFICATION_ID = 42;

    private MediaPlayer player;
    private AudioRouting.OnRoutingChangedListener routingListener;
    private String requestedLabel = "System default";
    private boolean routeAccepted = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        writeState(false, "System default", "—", false, "Service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopPlaybackAndSelf("Stopped by user");
            return START_NOT_STICKY;
        }

        if (ACTION_PLAY.equals(action)) {
            requestedLabel = intent.getStringExtra(EXTRA_DEVICE_LABEL);
            if (requestedLabel == null || requestedLabel.trim().isEmpty()) requestedLabel = "System default";

            startAsMediaForeground("Preparing · requested: " + requestedLabel);

            String uriText = intent.getStringExtra(EXTRA_URI);
            Integer deviceId = intent.hasExtra(EXTRA_DEVICE_ID)
                    ? intent.getIntExtra(EXTRA_DEVICE_ID, -1)
                    : null;
            if (uriText != null) play(Uri.parse(uriText), deviceId);
        }
        return START_NOT_STICKY;
    }

    private void startAsMediaForeground(String text) {
        Notification n = buildNotification(text);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                    NOTIFICATION_ID,
                    n,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
    }

    private void play(Uri uri, Integer deviceId) {
        releasePlayer();

        MediaPlayer p = new MediaPlayer();
        player = p;

        try {
            p.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());

            // Deliberate experiment: DO NOT request AudioManager audio focus here.
            // The goal is to see whether SoundSplit can remain active when TikTok requests focus.

            p.setDataSource(this, uri);
            p.setLooping(true);

            routingListener = router -> updateActualRoute("Route changed");
            p.addOnRoutingChangedListener(routingListener, null);

            p.setOnPreparedListener(prepared -> {
                AudioDeviceInfo preferred = deviceId == null ? null : findOutputById(deviceId);
                try {
                    routeAccepted = prepared.setPreferredDevice(preferred);
                } catch (RuntimeException e) {
                    routeAccepted = false;
                }

                try {
                    prepared.start();
                    updateActualRoute("Playback started");
                } catch (RuntimeException e) {
                    writeState(false, requestedLabel, "—", routeAccepted,
                            "Start failed: " + e.getClass().getSimpleName());
                    updateNotification("Could not start playback");
                }
            });

            p.setOnErrorListener((mp, what, extra) -> {
                writeState(false, requestedLabel, "—", routeAccepted,
                        "Playback error " + what + "/" + extra);
                updateNotification("Playback error " + what + "/" + extra);
                return true;
            });

            p.setOnCompletionListener(mp -> updateActualRoute("Track completed"));
            p.prepareAsync();
        } catch (Exception e) {
            writeState(false, requestedLabel, "—", false,
                    "Prepare failed: " + e.getClass().getSimpleName());
            updateNotification("Could not prepare audio");
            releasePlayer();
        }
    }

    private AudioDeviceInfo findOutputById(int deviceId) {
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        try {
            for (AudioDeviceInfo d : audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                if (d.getId() == deviceId) return d;
            }
        } catch (SecurityException ignored) {
        }
        return null;
    }

    private void updateActualRoute(String event) {
        if (player == null) return;
        String actual = "Unknown / not reported";
        try {
            AudioDeviceInfo routed = player.getRoutedDevice();
            if (routed != null) actual = readableDeviceName(routed);
        } catch (RuntimeException ignored) {
        }

        boolean playing = false;
        try { playing = player.isPlaying(); } catch (RuntimeException ignored) {}

        writeState(playing, requestedLabel, actual, routeAccepted, event);
        if (playing) updateNotification("Actual output: " + actual);
    }

    private String readableDeviceName(AudioDeviceInfo d) {
        String type;
        switch (d.getType()) {
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER: type = "Phone speaker"; break;
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES: type = "Wired headphones"; break;
            case AudioDeviceInfo.TYPE_WIRED_HEADSET: type = "Wired headset"; break;
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP: type = "Bluetooth A2DP"; break;
            case AudioDeviceInfo.TYPE_USB_DEVICE: type = "USB audio"; break;
            case AudioDeviceInfo.TYPE_USB_HEADSET: type = "USB headset"; break;
            case AudioDeviceInfo.TYPE_HDMI: type = "HDMI"; break;
            default:
                if (Build.VERSION.SDK_INT >= 31 && d.getType() == AudioDeviceInfo.TYPE_BLE_HEADSET) {
                    type = "Bluetooth LE headset";
                } else if (Build.VERSION.SDK_INT >= 31 && d.getType() == AudioDeviceInfo.TYPE_BLE_SPEAKER) {
                    type = "Bluetooth LE speaker";
                } else if (Build.VERSION.SDK_INT >= 33 && d.getType() == AudioDeviceInfo.TYPE_BLE_BROADCAST) {
                    type = "Bluetooth LE broadcast";
                } else {
                    type = "Audio output";
                }
        }
        String product = "";
        try {
            CharSequence p = d.getProductName();
            product = p == null ? "" : p.toString().trim();
        } catch (SecurityException ignored) {
        }
        if (product.isEmpty() || product.equalsIgnoreCase(type)) return type;
        return type + " · " + product;
    }

    private void stopPlaybackAndSelf(String event) {
        releasePlayer();
        writeState(false, requestedLabel, "—", routeAccepted, event);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void releasePlayer() {
        MediaPlayer p = player;
        player = null;
        if (p == null) return;
        try {
            if (routingListener != null) p.removeOnRoutingChangedListener(routingListener);
        } catch (RuntimeException ignored) {}
        try { p.stop(); } catch (RuntimeException ignored) {}
        try { p.release(); } catch (RuntimeException ignored) {}
        routingListener = null;
    }

    @Override
    public void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "SoundSplit playback",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Keeps SoundSplit test audio active in the background.");
        manager.createNotificationChannel(channel);
    }

    private Notification buildNotification(String text) {
        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent openPending = PendingIntent.getActivity(
                this,
                0,
                openApp,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent stop = new Intent(this, AudioPlaybackService.class);
        stop.setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(
                this,
                1,
                stop,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("SoundSplit test running")
                .setContentText(text)
                .setContentIntent(openPending)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(new Notification.Action.Builder(null, "Stop", stopPending).build())
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, buildNotification(text));
    }

    private void writeState(boolean playing, String requested, String actual,
                            boolean accepted, String event) {
        SharedPreferences.Editor e = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        e.putBoolean(KEY_PLAYING, playing);
        e.putString(KEY_REQUESTED_ROUTE, requested == null ? "—" : requested);
        e.putString(KEY_ACTUAL_ROUTE, actual == null ? "—" : actual);
        e.putBoolean(KEY_ROUTE_ACCEPTED, accepted);
        e.putString(KEY_LAST_EVENT, event == null ? "—" : event);
        e.apply();
    }
}
