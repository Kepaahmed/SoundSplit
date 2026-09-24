package com.soundsplit.prototype;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.spotify.android.appremote.api.ConnectionParams;
import com.spotify.android.appremote.api.Connector;
import com.spotify.android.appremote.api.SpotifyAppRemote;
import com.spotify.protocol.client.Subscription;
import com.spotify.protocol.types.PlayerState;
import com.spotify.protocol.types.Track;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQUEST_AUDIO_FILE = 1001;
    private static final int REQUEST_PERMISSIONS = 1002;

    // Spotify Client ID is public application identification, not a client secret.
    private static final String SPOTIFY_CLIENT_ID = "e9adac45b83e4d4fa3ba6089f56b7f46";
    private static final String SPOTIFY_REDIRECT_URI = "soundsplit://callback";

    private static final int BG = Color.rgb(247, 247, 251);
    private static final int CARD = Color.WHITE;
    private static final int TEXT = Color.rgb(28, 28, 35);
    private static final int MUTED = Color.rgb(95, 95, 108);
    private static final int PURPLE = Color.rgb(103, 80, 164);
    private static final int GREEN = Color.rgb(32, 122, 67);
    private static final int ORANGE = Color.rgb(168, 98, 0);

    private static final class OutputChoice {
        final Integer deviceId;
        final String label;

        OutputChoice(Integer deviceId, String label) {
            this.deviceId = deviceId;
            this.label = label;
        }

        @Override public String toString() { return label; }
    }

    private final List<OutputChoice> outputChoices = new ArrayList<>();
    private Uri selectedAudioUri;
    private TextView fileStatus;
    private TextView routeStatus;
    private TextView liveStatus;
    private Spinner outputSpinner;

    private SpotifyAppRemote spotifyAppRemote;
    private Subscription<PlayerState> spotifyStateSubscription;
    private TextView spotifyStatus;
    private TextView spotifyTrack;
    private Button spotifyPlayPauseButton;
    private boolean spotifyPaused = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        requestRuntimePermissions();
        refreshOutputs();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshLiveStatus();
    }

    @Override
    protected void onStop() {
        disconnectSpotify();
        super.onStop();
    }

    private void buildUi() {
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= 23) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(24), dp(18), dp(40));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text("SoundSplit", 30, TEXT, Typeface.BOLD);
        root.addView(title);

        TextView subtitle = text(
                "Native prototype · keep SoundSplit music alive while another app starts media.",
                15, MUTED, Typeface.NORMAL);
        subtitle.setPadding(0, dp(4), 0, dp(18));
        root.addView(subtitle);

        root.addView(infoBox(
                "What we are testing",
                "SoundSplit will intentionally NOT request normal Android audio focus. It will request a specific output device for its own player and continue in a media-playback foreground service.",
                PURPLE));

        LinearLayout spotifyCard = card();
        spotifyCard.addView(sectionTitle("Spotify control · v0.3"));
        spotifyStatus = body("Not connected to Spotify.");
        spotifyStatus.setTextColor(ORANGE);
        spotifyCard.addView(spotifyStatus);
        spotifyTrack = body("Current track: —");
        spotifyCard.addView(spotifyTrack);
        spotifyCard.addView(primaryButton("Connect Spotify", v -> connectSpotify()));
        spotifyCard.addView(secondaryButton("Open Spotify to choose music", v -> openSpotify()));

        LinearLayout spotifyControls = new LinearLayout(this);
        spotifyControls.setOrientation(LinearLayout.HORIZONTAL);
        spotifyControls.setGravity(Gravity.CENTER);
        Button previous = secondaryButton("Previous", v -> spotifyPrevious());
        spotifyPlayPauseButton = secondaryButton("Play / Pause", v -> spotifyTogglePlayPause());
        Button next = secondaryButton("Next", v -> spotifyNext());
        spotifyControls.addView(previous, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        spotifyControls.addView(spotifyPlayPauseButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        spotifyControls.addView(next, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        spotifyCard.addView(spotifyControls);

        TextView spotifyNote = body(
                "Spotify mode controls the installed Spotify app. Spotify still owns its audio focus and output route, so this mode does not yet inherit SoundSplit's protected local-player routing.");
        spotifyNote.setTextSize(12);
        spotifyNote.setTextColor(MUTED);
        spotifyCard.addView(spotifyNote);
        root.addView(spotifyCard);

        LinearLayout musicCard = card();
        musicCard.addView(sectionTitle("1  Choose a test song"));
        fileStatus = body("No song selected yet.");
        musicCard.addView(fileStatus);
        musicCard.addView(primaryButton("Choose audio file", v -> chooseAudioFile()));
        root.addView(musicCard);

        LinearLayout routeCard = card();
        routeCard.addView(sectionTitle("2  Choose SoundSplit output"));
        outputSpinner = new Spinner(this);
        outputSpinner.setMinimumHeight(dp(52));
        routeCard.addView(outputSpinner, matchWrap());
        routeStatus = body("Connect your Bluetooth speaker, then refresh outputs.");
        routeStatus.setPadding(0, dp(8), 0, dp(8));
        routeCard.addView(routeStatus);
        routeCard.addView(secondaryButton("Refresh output devices", v -> refreshOutputs()));
        root.addView(routeCard);

        LinearLayout playCard = card();
        playCard.addView(sectionTitle("3  Start native playback"));
        playCard.addView(primaryButton("Start protected playback", v -> startProtectedPlayback()));
        Button stop = secondaryButton("Stop playback", v -> stopPlayback());
        stop.setPadding(stop.getPaddingLeft(), stop.getPaddingTop(), stop.getPaddingRight(), stop.getPaddingBottom());
        playCard.addView(stop);
        root.addView(playCard);

        LinearLayout diagCard = card();
        diagCard.addView(sectionTitle("Live routing diagnostic"));
        liveStatus = body("Playback has not started.");
        liveStatus.setTextColor(GREEN);
        diagCard.addView(liveStatus);
        diagCard.addView(secondaryButton("Refresh diagnostic", v -> refreshLiveStatus()));
        root.addView(diagCard);

        LinearLayout testCard = card();
        testCard.addView(sectionTitle("4  Test with TikTok"));
        testCard.addView(step("A", "Start SoundSplit", "Choose the Bluetooth speaker and start the song."));
        testCard.addView(step("B", "Open TikTok", "Leave SoundSplit running and start a TikTok video with sound."));
        testCard.addView(step("C", "Listen carefully", "Does SoundSplit continue? Does TikTok also play? Which speaker does each use?"));
        testCard.addView(step("D", "Come back here", "Tap Refresh diagnostic to see the route Android reports for SoundSplit."));
        root.addView(testCard);

        LinearLayout fallbackCard = card();
        fallbackCard.addView(sectionTitle("If TikTok also uses Bluetooth"));
        fallbackCard.addView(body(
                "While SoundSplit is still playing, open Android's media-output controls and switch the phone's general media output to ‘This phone’ / phone speaker, then return to TikTok. The experiment is whether SoundSplit's preferred Bluetooth route stays pinned while the system default moves back to the phone."));
        fallbackCard.addView(secondaryButton("Open Bluetooth settings", v -> {
            try {
                startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
            } catch (Exception e) {
                Toast.makeText(this, "Open Bluetooth settings from your phone settings.", Toast.LENGTH_SHORT).show();
            }
        }));
        root.addView(fallbackCard);

        TextView note = body(
                "Prototype limitation: Android can reject or override an app's preferred route. SoundSplit cannot force TikTok itself to use the phone speaker with ordinary public app permissions.");
        note.setTextSize(12);
        note.setTextColor(MUTED);
        note.setPadding(dp(4), dp(6), dp(4), 0);
        root.addView(note);

        setContentView(scroll);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(makeRoundedDrawable(CARD, dp(18)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(lp);
        return card;
    }

    private android.graphics.drawable.GradientDrawable makeRoundedDrawable(int color, int radius) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        d.setStroke(dp(1), Color.rgb(225, 225, 232));
        return d;
    }

    private TextView sectionTitle(String value) {
        TextView v = text(value, 19, TEXT, Typeface.BOLD);
        v.setPadding(0, 0, 0, dp(10));
        return v;
    }

    private TextView body(String value) {
        TextView v = text(value, 15, MUTED, Typeface.NORMAL);
        v.setLineSpacing(0, 1.12f);
        v.setPadding(0, 0, 0, dp(10));
        return v;
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setTypeface(Typeface.create("sans", style));
        return v;
    }

    private View infoBox(String heading, String detail, int accent) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        android.graphics.drawable.GradientDrawable d = makeRoundedDrawable(Color.rgb(242, 239, 250), dp(16));
        d.setStroke(dp(1), Color.rgb(220, 211, 242));
        box.setBackground(d);
        TextView h = text(heading, 16, accent, Typeface.BOLD);
        TextView p = text(detail, 14, TEXT, Typeface.NORMAL);
        p.setPadding(0, dp(5), 0, 0);
        p.setLineSpacing(0, 1.12f);
        box.addView(h);
        box.addView(p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(12));
        box.setLayoutParams(lp);
        return box;
    }

    private Button primaryButton(String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setMinHeight(dp(52));
        android.graphics.drawable.GradientDrawable d = makeRoundedDrawable(PURPLE, dp(14));
        d.setStroke(0, PURPLE);
        b.setBackground(d);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(4), 0, dp(8));
        b.setLayoutParams(lp);
        return b;
    }

    private Button secondaryButton(String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(TEXT);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setMinHeight(dp(50));
        b.setBackground(makeRoundedDrawable(Color.rgb(246, 246, 249), dp(14)));
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(4), 0, dp(4));
        b.setLayoutParams(lp);
        return b;
    }

    private View step(String letter, String heading, String detail) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, dp(7), 0, dp(7));

        TextView badge = text(letter, 14, Color.WHITE, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(makeRoundedDrawable(PURPLE, dp(10)));
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(dp(34), dp(34));
        badgeLp.setMargins(0, 0, dp(12), 0);
        row.addView(badge, badgeLp);

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView h = text(heading, 15, TEXT, Typeface.BOLD);
        TextView p = text(detail, 14, MUTED, Typeface.NORMAL);
        p.setLineSpacing(0, 1.1f);
        words.addView(h);
        words.addView(p);
        row.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void connectSpotify() {
        if (spotifyAppRemote != null) {
            spotifyStatus.setText("Spotify is already connected.");
            spotifyStatus.setTextColor(GREEN);
            return;
        }

        spotifyStatus.setText("Connecting to Spotify…");
        spotifyStatus.setTextColor(ORANGE);

        ConnectionParams params = new ConnectionParams.Builder(SPOTIFY_CLIENT_ID)
                .setRedirectUri(SPOTIFY_REDIRECT_URI)
                .showAuthView(true)
                .build();

        SpotifyAppRemote.connect(this, params, new Connector.ConnectionListener() {
            @Override
            public void onConnected(SpotifyAppRemote appRemote) {
                spotifyAppRemote = appRemote;
                runOnUiThread(() -> {
                    spotifyStatus.setText("Connected to Spotify.");
                    spotifyStatus.setTextColor(GREEN);
                });
                subscribeToSpotifyState();
            }

            @Override
            public void onFailure(Throwable error) {
                String message = error == null ? "Unknown connection error" : error.getClass().getSimpleName();
                if (error != null && error.getMessage() != null && !error.getMessage().trim().isEmpty()) {
                    message += ": " + error.getMessage();
                }
                final String finalMessage = message;
                runOnUiThread(() -> {
                    spotifyStatus.setText("Spotify connection failed: " + finalMessage);
                    spotifyStatus.setTextColor(ORANGE);
                });
            }
        });
    }

    private void subscribeToSpotifyState() {
        if (spotifyAppRemote == null) return;
        try {
            spotifyStateSubscription = (Subscription<PlayerState>) spotifyAppRemote.getPlayerApi()
                    .subscribeToPlayerState()
                    .setEventCallback(this::showSpotifyPlayerState)
                    .setErrorCallback(error -> runOnUiThread(() -> {
                        spotifyStatus.setText("Spotify state error: " + error.getClass().getSimpleName());
                        spotifyStatus.setTextColor(ORANGE);
                    }));
        } catch (RuntimeException e) {
            spotifyStatus.setText("Could not subscribe to Spotify state: " + e.getClass().getSimpleName());
            spotifyStatus.setTextColor(ORANGE);
        }
    }

    private void showSpotifyPlayerState(PlayerState state) {
        if (state == null) return;
        spotifyPaused = state.isPaused;
        Track track = state.track;
        String line = "Current track: —";
        if (track != null) {
            String artist = (track.artist == null || track.artist.name == null) ? "Unknown artist" : track.artist.name;
            line = "Current track: " + track.name + " · " + artist;
        }
        final String display = line;
        runOnUiThread(() -> {
            spotifyTrack.setText(display);
            spotifyPlayPauseButton.setText(spotifyPaused ? "Play" : "Pause");
        });
    }

    private boolean requireSpotifyConnection() {
        if (spotifyAppRemote != null) return true;
        Toast.makeText(this, "Connect Spotify first.", Toast.LENGTH_SHORT).show();
        return false;
    }

    private void spotifyTogglePlayPause() {
        if (!requireSpotifyConnection()) return;
        if (spotifyPaused) {
            spotifyAppRemote.getPlayerApi().resume()
                    .setErrorCallback(error -> showSpotifyCommandError("Play", error));
        } else {
            spotifyAppRemote.getPlayerApi().pause()
                    .setErrorCallback(error -> showSpotifyCommandError("Pause", error));
        }
    }

    private void spotifyPrevious() {
        if (!requireSpotifyConnection()) return;
        spotifyAppRemote.getPlayerApi().skipPrevious()
                .setErrorCallback(error -> showSpotifyCommandError("Previous", error));
    }

    private void spotifyNext() {
        if (!requireSpotifyConnection()) return;
        spotifyAppRemote.getPlayerApi().skipNext()
                .setErrorCallback(error -> showSpotifyCommandError("Next", error));
    }

    private void showSpotifyCommandError(String command, Throwable error) {
        final String detail = error == null ? "Unknown error" : error.getClass().getSimpleName();
        runOnUiThread(() -> Toast.makeText(this, command + " failed: " + detail, Toast.LENGTH_SHORT).show());
    }

    private void openSpotify() {
        Intent launch = getPackageManager().getLaunchIntentForPackage("com.spotify.music");
        if (launch != null) {
            startActivity(launch);
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.spotify.music")));
        } catch (Exception ignored) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.spotify.music")));
        }
    }

    private void disconnectSpotify() {
        if (spotifyStateSubscription != null) {
            try { spotifyStateSubscription.cancel(); } catch (RuntimeException ignored) {}
            spotifyStateSubscription = null;
        }
        if (spotifyAppRemote != null) {
            try { SpotifyAppRemote.disconnect(spotifyAppRemote); } catch (RuntimeException ignored) {}
            spotifyAppRemote = null;
        }
        if (spotifyStatus != null) {
            spotifyStatus.setText("Not connected to Spotify.");
            spotifyStatus.setTextColor(MUTED);
        }
    }

    private void chooseAudioFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_AUDIO_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_AUDIO_FILE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            selectedAudioUri = uri;
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
            String label = uri.getLastPathSegment();
            fileStatus.setText("Selected: " + (label == null ? "audio file" : label));
        }
    }

    private void requestRuntimePermissions() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31 &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!permissions.isEmpty()) {
            requestPermissions(permissions.toArray(new String[0]), REQUEST_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) refreshOutputs();
    }

    private void refreshOutputs() {
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        AudioDeviceInfo[] devices;
        try {
            devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        } catch (SecurityException e) {
            routeStatus.setText("Bluetooth permission is needed before connected outputs can be inspected.");
            requestRuntimePermissions();
            return;
        }

        List<AudioDeviceInfo> useful = new ArrayList<>();
        Collections.addAll(useful, devices);
        useful.removeIf(device -> !isUsefulOutput(device));
        useful.sort(Comparator.comparing(this::deviceLabel, String.CASE_INSENSITIVE_ORDER));

        outputChoices.clear();
        outputChoices.add(new OutputChoice(null, "System default"));
        for (AudioDeviceInfo device : useful) {
            outputChoices.add(new OutputChoice(device.getId(), deviceLabel(device)));
        }

        ArrayAdapter<OutputChoice> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                outputChoices);
        outputSpinner.setAdapter(adapter);

        if (useful.isEmpty()) {
            routeStatus.setText("No useful output detected. Connect Bluetooth, then tap refresh.");
            routeStatus.setTextColor(ORANGE);
        } else {
            routeStatus.setText("Found " + useful.size() + " output device(s). Select your Bluetooth speaker above.");
            routeStatus.setTextColor(GREEN);
        }
    }

    private boolean isUsefulOutput(AudioDeviceInfo d) {
        int t = d.getType();
        if (t == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
                t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                t == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                t == AudioDeviceInfo.TYPE_USB_DEVICE ||
                t == AudioDeviceInfo.TYPE_USB_HEADSET ||
                t == AudioDeviceInfo.TYPE_HDMI) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= 31) {
            return t == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    t == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                    t == AudioDeviceInfo.TYPE_BLE_BROADCAST;
        }
        return false;
    }

    private String deviceLabel(AudioDeviceInfo d) {
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

    private void startProtectedPlayback() {
        if (selectedAudioUri == null) {
            Toast.makeText(this, "Choose an audio file first.", Toast.LENGTH_SHORT).show();
            return;
        }

        OutputChoice choice = (OutputChoice) outputSpinner.getSelectedItem();
        if (choice == null) choice = new OutputChoice(null, "System default");

        Intent intent = new Intent(this, AudioPlaybackService.class);
        intent.setAction(AudioPlaybackService.ACTION_PLAY);
        intent.putExtra(AudioPlaybackService.EXTRA_URI, selectedAudioUri.toString());
        intent.putExtra(AudioPlaybackService.EXTRA_DEVICE_LABEL, choice.label);
        if (choice.deviceId != null) {
            intent.putExtra(AudioPlaybackService.EXTRA_DEVICE_ID, choice.deviceId);
        }
        startForegroundService(intent);
        Toast.makeText(this, "SoundSplit started. Now open TikTok and play a video.", Toast.LENGTH_LONG).show();

        liveStatus.postDelayed(this::refreshLiveStatus, 1200);
    }

    private void stopPlayback() {
        Intent intent = new Intent(this, AudioPlaybackService.class);
        intent.setAction(AudioPlaybackService.ACTION_STOP);
        startService(intent);
        liveStatus.postDelayed(this::refreshLiveStatus, 250);
    }

    private void refreshLiveStatus() {
        android.content.SharedPreferences p = getSharedPreferences(AudioPlaybackService.PREFS, MODE_PRIVATE);
        boolean playing = p.getBoolean(AudioPlaybackService.KEY_PLAYING, false);
        String requested = p.getString(AudioPlaybackService.KEY_REQUESTED_ROUTE, "—");
        String actual = p.getString(AudioPlaybackService.KEY_ACTUAL_ROUTE, "—");
        boolean accepted = p.getBoolean(AudioPlaybackService.KEY_ROUTE_ACCEPTED, false);
        String event = p.getString(AudioPlaybackService.KEY_LAST_EVENT, "No playback event yet.");

        if (!playing) {
            liveStatus.setText("Not playing.\nLast event: " + event);
            liveStatus.setTextColor(MUTED);
            return;
        }

        String status = "Playing: YES\n" +
                "Requested output: " + requested + "\n" +
                "Android accepted preference: " + (accepted ? "YES" : "NO / DEFAULT") + "\n" +
                "Actual routed output: " + actual + "\n" +
                "Last event: " + event;
        liveStatus.setText(status);
        liveStatus.setTextColor(GREEN);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
