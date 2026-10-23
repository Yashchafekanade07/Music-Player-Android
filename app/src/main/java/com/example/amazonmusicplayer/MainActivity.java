
package com.example.amazonmusicplayer;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private MusicService musicService;
    private boolean bound = false;
    private boolean userSeeking = false;

    private SeekBar seekBar;
    private TextView tvSongTitle, tvArtist, tvCurrentTime, tvDuration;
    private TextView tvAlbumArt;

    private Button btnPlay, btnFavourite, btnFavourites;
    private Button btnShuffle, btnRepeat;

    private SharedPreferences preferences;
    private Set<String> favouriteSongs = new HashSet<>();
    private int currentSongNumber = 1;

    private final Handler handler = new Handler();

    private final String[] songTitles = {
            "", "Song 1", "Song 2", "Song 3"
    };

    private final String[] artistNames = {
            "", "Artist 1", "Artist 2", "Artist 3"
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.LocalBinder binder =
                    (MusicService.LocalBinder) service;

            musicService = binder.getService();
            bound = true;

            syncSongDetails();
            updateSeekBar();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
            musicService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    100
            );
        }

        preferences = getSharedPreferences("MusicPrefs", MODE_PRIVATE);

        favouriteSongs = new HashSet<>(
                preferences.getStringSet(
                        "favourites", new HashSet<>()
                )
        );

        // Find views
        seekBar = findViewById(R.id.seekBar);
        tvSongTitle = findViewById(R.id.tvSongTitle);
        tvArtist = findViewById(R.id.tvArtist);
        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        tvDuration = findViewById(R.id.tvDuration);
        tvAlbumArt = findViewById(R.id.tvAlbumArt);

        btnPlay = findViewById(R.id.btnPlay);
        btnFavourite = findViewById(R.id.btnFavourite);
        btnFavourites = findViewById(R.id.btnFavourites);

        Button btnNext = findViewById(R.id.btnNext);
        Button btnPrevious = findViewById(R.id.btnPrevious);
        Button btnStop = findViewById(R.id.btnStop);
        Button btnLyrics = findViewById(R.id.btnLyrics);
        Button btnQueue = findViewById(R.id.btnQueue);
        Button btnHome = findViewById(R.id.btnHome);
        Button btnLibrary = findViewById(R.id.btnLibrary);

        btnShuffle = findViewById(R.id.btnShuffle);
        btnRepeat = findViewById(R.id.btnRepeat);

        updateFavouriteButton();

        // Start and bind music service
        startService(new Intent(this, MusicService.class));

        bindService(
                new Intent(this, MusicService.class),
                connection,
                BIND_AUTO_CREATE
        );

        // Play / Pause
        btnPlay.setOnClickListener(v -> {
            sendAction("TOGGLE");
            handler.postDelayed(this::updatePlaybackButtons, 250);
        });

        // Next
        btnNext.setOnClickListener(v -> {
            sendAction("NEXT");
            handler.postDelayed(this::syncSongDetails, 250);
        });

        // Previous
        btnPrevious.setOnClickListener(v -> {
            sendAction("PREVIOUS");
            handler.postDelayed(this::syncSongDetails, 250);
        });

        // Stop
        btnStop.setOnClickListener(v -> {
            sendAction("STOP");

            btnPlay.setText("▶ Play");
            seekBar.setProgress(0);
            tvCurrentTime.setText("0:00");
            tvDuration.setText("0:00");
        });

        // Add / remove Favourite
        btnFavourite.setOnClickListener(v -> toggleFavourite());

        // Favourite list
        btnFavourites.setOnClickListener(v -> showFavourites());

        // Lyrics
        btnLyrics.setOnClickListener(v -> showLyrics());

        // Queue
        btnQueue.setOnClickListener(v -> showQueue());

        // Shuffle
        btnShuffle.setOnClickListener(v -> {
            sendAction("SHUFFLE");
            handler.postDelayed(this::updatePlaybackButtons, 200);
        });

        // Repeat
        btnRepeat.setOnClickListener(v -> {
            sendAction("REPEAT");
            handler.postDelayed(this::updatePlaybackButtons, 200);
        });

        // Home button
        btnHome.setOnClickListener(v ->
                Toast.makeText(
                        this,
                        "You are on Home",
                        Toast.LENGTH_SHORT
                ).show()
        );

        // Library button
        btnLibrary.setOnClickListener(v -> showQueue());

        // SeekBar
        seekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(
                            SeekBar bar, int progress, boolean fromUser) {

                        if (fromUser) {
                            tvCurrentTime.setText(formatTime(progress));
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar bar) {
                        userSeeking = true;
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar bar) {
                        if (bound && musicService != null) {
                            musicService.seekTo(bar.getProgress());
                        }

                        userSeeking = false;
                    }
                });
    }

    // Send actions to MusicService
    private void sendAction(String action) {
        Intent intent = new Intent(this, MusicService.class);
        intent.setAction(action);
        startService(intent);
    }

    // Update song name, artist and favourite icon
    private void syncSongDetails() {
        if (!bound || musicService == null) {
            return;
        }

        currentSongNumber = musicService.getCurrentSong();

        updateSongDetails(currentSongNumber);
        updateFavouriteButton();
        updatePlaybackButtons();
    }

    private void updateSongDetails(int number) {
        if (number < 1 || number > 3) {
            number = 1;
        }

        currentSongNumber = number;
        tvSongTitle.setText(songTitles[number]);
        tvArtist.setText(artistNames[number]);
        tvAlbumArt.setText("♫");
    }

    // Update Play/Pause, Shuffle and Repeat labels
    private void updatePlaybackButtons() {
        if (!bound || musicService == null) {
            return;
        }

        btnPlay.setText(
                musicService.isPlaying() ? "Ⅱ Pause" : "▶ Play"
        );

        btnShuffle.setText(
                musicService.isShuffleEnabled()
                        ? "Shuffle: ON" : "Shuffle: OFF"
        );

        btnRepeat.setText(
                musicService.isRepeatEnabled()
                        ? "Repeat: ON" : "Repeat: OFF"
        );
    }

    // Favourite button
    private void toggleFavourite() {
        if (bound && musicService != null) {
            currentSongNumber = musicService.getCurrentSong();
        }

        String songId = "song" + currentSongNumber;

        if (favouriteSongs.contains(songId)) {
            favouriteSongs.remove(songId);

            Toast.makeText(
                    this,
                    "Removed from Favourites",
                    Toast.LENGTH_SHORT
            ).show();
        } else {
            favouriteSongs.add(songId);

            Toast.makeText(
                    this,
                    "Added to Favourites ❤️",
                    Toast.LENGTH_SHORT
            ).show();
        }

        preferences.edit()
                .putStringSet(
                        "favourites",
                        new HashSet<>(favouriteSongs)
                )
                .apply();

        updateFavouriteButton();
    }

    private void updateFavouriteButton() {
        if (btnFavourite == null) {
            return;
        }

        String songId = "song" + currentSongNumber;

        btnFavourite.setText(
                favouriteSongs.contains(songId) ? "♥" : "♡"
        );
    }

    // Show saved favourites
    private void showFavourites() {
        ArrayList<String> songs = new ArrayList<>(favouriteSongs);

        if (songs.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Favourite Songs")
                    .setMessage("No favourite songs yet.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        showSongList(songs, "❤️ Favourite Songs");
    }

    // Show all songs in queue
    private void showQueue() {
        ArrayList<String> songs = new ArrayList<>();
        songs.add("song1");
        songs.add("song2");
        songs.add("song3");

        showSongList(songs, "Song Queue");
    }

    // Common song list for Queue, Library and Favourites
    private void showSongList(
            ArrayList<String> songs, String title) {

        songs.sort((a, b) -> Integer.compare(
                Integer.parseInt(a.replace("song", "")),
                Integer.parseInt(b.replace("song", ""))
        ));

        String[] names = new String[songs.size()];

        for (int i = 0; i < songs.size(); i++) {
            int number = Integer.parseInt(
                    songs.get(i).replace("song", "")
            );

            names[i] = songTitles[number];
        }

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(names, (dialog, which) -> {
                    int number = Integer.parseInt(
                            songs.get(which).replace("song", "")
                    );

                    currentSongNumber = number;

                    Intent intent = new Intent(
                            MainActivity.this, MusicService.class
                    );
                    intent.setAction("PLAY_SONG_" + number);
                    startService(intent);

                    updateSongDetails(number);
                    updateFavouriteButton();

                    handler.postDelayed(
                            this::updatePlaybackButtons, 300
                    );
                })
                .setNegativeButton("Close", null)
                .show();
    }

    // Lyrics dialog
    private void showLyrics() {
        if (bound && musicService != null) {
            currentSongNumber = musicService.getCurrentSong();
        }

        String lyrics;

        switch (currentSongNumber) {
            case 1:
                lyrics = "Add the actual lyrics for Song 1 here.";
                break;

            case 2:
                lyrics = "Add the actual lyrics for Song 2 here.";
                break;

            default:
                lyrics = "Add the actual lyrics for Song 3 here.";
                break;
        }

        new AlertDialog.Builder(this)
                .setTitle("Lyrics - " + songTitles[currentSongNumber])
                .setMessage(lyrics)
                .setPositiveButton("Close", null)
                .show();
    }

    // Update SeekBar and current playback time
    private void updateSeekBar() {
        if (bound && musicService != null) {
            int duration = musicService.getDuration();
            int position = musicService.getCurrentPosition();

            seekBar.setMax(Math.max(duration, 1));

            if (!userSeeking) {
                seekBar.setProgress(position);
                tvCurrentTime.setText(formatTime(position));
            }

            tvDuration.setText(formatTime(duration));

            // Also detects automatic next song after completion
            syncSongDetails();
        }

        handler.postDelayed(this::updateSeekBar, 500);
    }

    private String formatTime(int milliseconds) {
        int seconds = Math.max(milliseconds, 0) / 1000;

        return String.format(
                java.util.Locale.getDefault(),
                "%d:%02d",
                seconds / 60,
                seconds % 60
        );
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);

        if (bound) {
            unbindService(connection);
            bound = false;
        }

        super.onDestroy();
    }
}
