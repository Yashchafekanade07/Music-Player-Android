
package com.example.amazonmusicplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import java.util.Random;

public class MusicService extends Service {

    private static final String CHANNEL_ID = "music_channel";
    private static final int NOTIFICATION_ID = 101;

    private MediaPlayer player;
    private int currentSong = 1;
    private boolean shuffle = false;
    private boolean repeat = false;

    private final IBinder binder = new LocalBinder();
    private final Random random = new Random();

    public class LocalBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            return START_NOT_STICKY;
        }

        String action = intent.getAction();

        switch (action) {
            case "PLAY":
                if (player == null) {
                    playSelectedSong();
                } else if (!player.isPlaying()) {
                    player.start();
                    updateNotification();
                }
                break;

            case "TOGGLE":
                togglePlayback();
                break;

            case "PAUSE":
                pausePlayback();
                break;

            case "STOP":
                stopPlayback();
                stopForeground(true);
                stopSelf();
                break;

            case "NEXT":
                playNext();
                break;

            case "PREVIOUS":
                playPrevious();
                break;

            case "SHUFFLE":
                shuffle = !shuffle;
                updateNotification();
                break;

            case "REPEAT":
                repeat = !repeat;
                updateNotification();
                break;

            default:
                if (action.startsWith("PLAY_SONG_")) {
                    try {
                        int number = Integer.parseInt(
                                action.substring("PLAY_SONG_".length())
                        );

                        if (number >= 1 && number <= 3) {
                            currentSong = number;
                            playSelectedSong();
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
                break;
        }

        return START_NOT_STICKY;
    }

    private void playSelectedSong() {
        // Start foreground before preparing the player.
        startForegroundNotification();
        releasePlayer();

        int resourceId;

        if (currentSong == 1) {
            resourceId = R.raw.song1;
        } else if (currentSong == 2) {
            resourceId = R.raw.song2;
        } else {
            resourceId = R.raw.song3;
        }

        player = MediaPlayer.create(this, resourceId);

        if (player == null) {
            updateNotification();
            return;
        }

        player.setOnCompletionListener(mp -> {
            if (repeat) {
                mp.seekTo(0);
                mp.start();
                updateNotification();
            } else {
                playNext();
            }
        });

        player.start();
        updateNotification();
    }

    private void togglePlayback() {
        if (player == null) {
            playSelectedSong();
            return;
        }

        try {
            if (player.isPlaying()) {
                player.pause();
            } else {
                if (player.getCurrentPosition() >= player.getDuration()) {
                    player.seekTo(0);
                }
                player.start();
            }
            updateNotification();
        } catch (IllegalStateException ignored) {
            playSelectedSong();
        }
    }

    private void pausePlayback() {
        if (player != null) {
            try {
                if (player.isPlaying()) {
                    player.pause();
                }
            } catch (IllegalStateException ignored) {
            }
        }
        updateNotification();
    }

    private void playNext() {
        if (shuffle) {
            int nextSong;
            do {
                nextSong = random.nextInt(3) + 1;
            } while (nextSong == currentSong);

            currentSong = nextSong;
        } else {
            currentSong = (currentSong % 3) + 1;
        }

        playSelectedSong();
    }

    private void playPrevious() {
        if (player != null) {
            try {
                if (player.getCurrentPosition() > 3000) {
                    player.seekTo(0);
                    return;
                }
            } catch (IllegalStateException ignored) {
            }
        }

        if (shuffle) {
            int previousSong;
            do {
                previousSong = random.nextInt(3) + 1;
            } while (previousSong == currentSong);

            currentSong = previousSong;
        } else {
            currentSong--;
            if (currentSong < 1) {
                currentSong = 3;
            }
        }

        playSelectedSong();
    }

    private void stopPlayback() {
        releasePlayer();
    }

    private void releasePlayer() {
        if (player != null) {
            try {
                player.setOnCompletionListener(null);
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
    }

    public int getCurrentSong() {
        return currentSong;
    }

    public boolean isPlaying() {
        try {
            return player != null && player.isPlaying();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public boolean isShuffleEnabled() {
        return shuffle;
    }

    public boolean isRepeatEnabled() {
        return repeat;
    }

    public int getCurrentPosition() {
        try {
            return player == null ? 0 : player.getCurrentPosition();
        } catch (IllegalStateException e) {
            return 0;
        }
    }

    public int getDuration() {
        try {
            return player == null ? 0 : player.getDuration();
        } catch (IllegalStateException e) {
            return 0;
        }
    }

    public void seekTo(int position) {
        if (player != null) {
            try {
                player.seekTo(position);
            } catch (IllegalStateException ignored) {
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Music Player",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Music playback controls");

            NotificationManager manager =
                    getSystemService(NotificationManager.class);

            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private PendingIntent serviceAction(String action, int requestCode) {
        Intent intent = new Intent(this, MusicService.class);
        intent.setAction(action);

        return PendingIntent.getService(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private Notification buildNotification() {
        String title = "Song " + currentSong;
        boolean playing = isPlaying();

        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        PendingIntent openApp = PendingIntent.getActivity(
                this,
                1,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(
                        "AmazonMusicPlayer"
                                + (shuffle ? " • Shuffle" : "")
                                + (repeat ? " • Repeat" : "")
                )
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(openApp)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setOngoing(playing)
                .addAction(
                        android.R.drawable.ic_media_previous,
                        "Previous",
                        serviceAction("PREVIOUS", 2)
                )
                .addAction(
                        playing
                                ? android.R.drawable.ic_media_pause
                                : android.R.drawable.ic_media_play,
                        playing ? "Pause" : "Play",
                        serviceAction("TOGGLE", 3)
                )
                .addAction(
                        android.R.drawable.ic_media_next,
                        "Next",
                        serviceAction("NEXT", 4)
                )
                .build();
    }

    private void startForegroundNotification() {
        Notification notification = buildNotification();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void updateNotification() {
        NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    @Override
    public void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }
}
