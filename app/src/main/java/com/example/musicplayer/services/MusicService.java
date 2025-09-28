package com.example.musicplayer.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.media.session.MediaButtonReceiver;

import com.example.musicplayer.R;
import com.example.musicplayer.model.Song;
import com.example.musicplayer.presenter.MusicPresenter;
import com.example.musicplayer.view.MainActivity;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.PlaybackException;

import android.support.v4.media.session.PlaybackStateCompat;

/**
 * Serviço para reprodução de músicas em background.
 * Usa ExoPlayer para playback e notificações para controles.
 * Opera em background puro (sem foreground para evitar notificação persistente).
 */
public class MusicService extends Service {
    private static final String TAG = "MusicService";
    private final IBinder binder = new MusicBinder();
    private ExoPlayer player;
    private Song currentSong;
    private String currentTitle = "Música";
    private String currentArtist = "Artista";
    private static final String CHANNEL_ID = "MusicPlayerChannel";
    private MusicPresenter musicPresenter; // Referência para o presenter

    /**
     * Binder para os clientes do serviço.
     */
    public class MusicBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        player = new ExoPlayer.Builder(this).build();
        player.addListener(new Player.Listener() {
            @Override
            public void onMediaItemTransition(MediaItem mediaItem, int reason) {
                if (currentSong != null) {
                    updateCurrentTrack(currentSong.getTitle(), currentSong.getArtist());
                    updateNotification();
                    if (musicPresenter != null) {
                        musicPresenter.onSongChanged(currentSong);
                    }
                }
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_ENDED) {
                    if (musicPresenter != null) {
                        musicPresenter.nextSong();
                    }
                }
            }
        });
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /**
     * Define o presenter para o serviço.
     * @param presenter O MusicPresenter.
     */
    public void setPresenter(MusicPresenter presenter) {
        this.musicPresenter = presenter;
    }

    /**
     * Toca uma música específica.
     * @param song A música a ser tocada.
     */
    public void playSong(Song song) {
        if (song == null) {
            Log.e(TAG, "Song is null. Cannot play.");
            return;
        }
        this.currentSong = song;
        updateCurrentTrack(song.getTitle(), song.getArtist());
        MediaItem mediaItem = MediaItem.fromUri(Uri.parse(song.getPath()));
        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();
        updateNotification();
        Log.d(TAG, "Playing song: " + song.getTitle());
    }

    /**
     * Alterna entre tocar e pausar.
     */
    public void playPause() {
        if (player.isPlaying()) {
            player.pause();
        } else {
            player.play();
        }
        updateNotification();
    }

    /**
     * Move a reprodução para uma posição específica.
     * @param positionMs Posição em milissegundos.
     */
    public void seekTo(long positionMs) {
        if (player != null) {
            player.seekTo(positionMs);
        }
    }

    /**
     * Retorna a posição atual da música.
     * @return Posição em milissegundos.
     */
    public long getCurrentPosition() {
        return player != null ? player.getCurrentPosition() : 0;
    }

    /**
     * Retorna a duração total da música.
     * @return Duração em milissegundos.
     */
    public long getDuration() {
        return player != null ? player.getDuration() : 0;
    }

    /**
     * Define o modo de repetição.
     * @param repeat True para repetição, false caso contrário.
     */
    public void setRepeatMode(boolean repeat) {
        player.setRepeatMode(repeat ? Player.REPEAT_MODE_ALL : Player.REPEAT_MODE_OFF);
    }

    /**
     * Cria o canal de notificação para Android Oreo (API 26+) e superiores.
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Music Player";
            String description = "Channel for music playback notifications";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Constrói a notificação de reprodução.
     * @return A notificação.
     */
    private Notification buildNotification() {
        Intent playPauseIntent = new Intent(this, MusicService.class).setAction("ACTION_PLAY_PAUSE");
        PendingIntent playPausePendingIntent = PendingIntent.getService(this, 0, playPauseIntent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification) // O erro ocorre aqui, ic_notification
                .setContentTitle(currentTitle)
                .setContentText(currentArtist)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(player.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play_arrow,
                        "Play/Pause", playPausePendingIntent);

        return builder.build();
    }

    /**
     * Atualiza a notificação.
     * A notificação agora é puramente informativa e não está vinculada a um serviço em primeiro plano.
     */
    private void updateNotification() {
        NotificationManagerCompat manager = NotificationManagerCompat.from(this);
        if (manager.areNotificationsEnabled()) {
            manager.notify(1, buildNotification());
            Log.d(TAG, "Notification updated (background mode)");
        }
    }

    /**
     * Atualiza título e artista atuais.
     * @param title Novo título.
     * @param artist Novo artista.
     */
    private void updateCurrentTrack(String title, String artist) {
        this.currentTitle = title != null ? title : "Sem título";
        this.currentArtist = artist != null ? artist : "Desconhecido";
    }

    /**
     * Retorna a música atual.
     * @return Song atual.
     */
    public Song getCurrentSong() {
        return currentSong;
    }

    /**
     * Verifica se está tocando.
     * @return True se tocando.
     */
    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Lida com intents de alarm/notificações
        if (intent != null && "play".equals(intent.getAction())) {
            Log.d(TAG, "Playback started from alarm");
            // Adicione lógica para tocar música específica
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
        NotificationManagerCompat.from(this).cancel(1);
    }

    /**
     * Retorna o player do serviço.
     * @return O ExoPlayer.
     */
    public ExoPlayer getPlayer() {
        return player;
    }
}