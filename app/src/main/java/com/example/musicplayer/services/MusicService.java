package com.example.musicplayer.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.musicplayer.R;
import com.example.musicplayer.model.Song;
import com.example.musicplayer.presenter.MusicPresenter;
import com.example.musicplayer.view.MusicPlayerActivity;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;

import java.util.List;

public class MusicService extends Service {
    private static final String TAG = "MusicService";
    private static final String CHANNEL_ID = "music_player_channel";
    private static final int NOTIFICATION_ID = 101;

    // Constantes para ações
    public static final String ACTION_PLAY = "PLAY";
    public static final String ACTION_PAUSE = "PAUSE";
    public static final String ACTION_PLAY_PAUSE = "ACTION_PLAY_PAUSE";
    public static final String ACTION_NEXT = "ACTION_NEXT";
    public static final String ACTION_PREV = "ACTION_PREV";
    public static final String ACTION_STOP = "ACTION_STOP";

    // Componentes principais
    private ExoPlayer player;
    private Song currentSong;
    private MusicPresenter presenter;
    private boolean isShuffleMode = false;
    private boolean isRepeatMode = false;
    private List<Song> playlist;
    private int currentSongIndex = -1;
    private boolean isInForeground = false;
    private boolean isBound = false;

    // Sistema de Media e Notificações
    private MediaSessionCompat mediaSession;
    private NotificationManager notificationManager;

    // Gerenciamento de recursos
    private AudioManager audioManager;

    // Estado para restauração
    private boolean wasPlayingBeforeInterruption = false;
    private long playbackPositionBeforeInterruption = 0;
    private Song lastKnownSong = null;

    // Handler para transições automáticas
    private Handler transitionHandler = new Handler();

    // Handler para atualização automática do MediaSession
    private Handler progressHandler = new Handler();
    private Runnable progressUpdateRunnable;

    // Controle para evitar loop e garantir transição automática
    private boolean isProcessingTransition = false;
    private boolean isAutoTransitionEnabled = true;

    private final IBinder binder = new MusicBinder();

    // ============================================================
    // INTERFACE PARA CONTROLE DE TRANSIÇÃO AUTOMÁTICA
    // ============================================================

    public interface OnPlaybackStateChangedListener {
        void onPlaybackCompleted();
        void onSongChanged(Song song);
    }

    private OnPlaybackStateChangedListener playbackStateChangedListener;

    public void setPlaybackStateChangedListener(OnPlaybackStateChangedListener listener) {
        this.playbackStateChangedListener = listener;
        Log.d(TAG, "PlaybackStateChangedListener configurado: " + (listener != null));
    }

    private void notifySongChanged(Song song) {
        if (playbackStateChangedListener != null) {
            playbackStateChangedListener.onSongChanged(song);
            Log.d(TAG, "Notificando mudança de música: " + song.getTitle());
        }
    }

    private void notifyPlaybackCompleted() {
        if (playbackStateChangedListener != null) {
            playbackStateChangedListener.onPlaybackCompleted();
            Log.d(TAG, "Notificando conclusão de playback");
        }
    }

    public class MusicBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "MusicService onCreate - Iniciando serviço");

        initializeMediaSession();
        createNotificationChannel();
        initializePlayer();
        setupAudioFocus();

        Log.d(TAG, "MusicService criado em modo background");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service onStartCommand - Action: " +
                (intent != null ? intent.getAction() : "null"));

        if (intent != null) {
            String action = intent.getAction();

            if (isBound && presenter != null) {
                handleIncomingAction(action, intent);
            } else if (action != null && action.equals(ACTION_PLAY)) {
                handlePlayAction(intent);
                promoteToForeground();
            } else if (action != null) {
                handleIncomingAction(action, intent);
            }
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Service bound via Binder");
        isBound = true;
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "Service unbound");
        isBound = false;
        return super.onUnbind(intent);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "onTaskRemoved - App removido da recent tasks");

        if (isPlaying()) {
            promoteToForeground();
        }

        super.onTaskRemoved(rootIntent);
    }


    @Override
    public void onDestroy() {
        Log.d(TAG, "MusicService onDestroy - Liberando recursos");

        stopMediaSessionUpdates();

        if (progressHandler != null) {
            progressHandler.removeCallbacksAndMessages(null);
        }

        if (transitionHandler != null) {
            transitionHandler.removeCallbacksAndMessages(null);
        }

        releaseAudioFocus();
        releaseMediaSession();
        releasePlayer();
        hideNotification();

        Log.d(TAG, "MusicService destruído com sucesso");
        super.onDestroy();
    }

    // ============================================================
    // INICIALIZAÇÃO DE COMPONENTES - ATUALIZADA
    // ============================================================

    private void initializeMediaSession() {
        try {
            mediaSession = new MediaSessionCompat(this, "MusicPlayer");
            mediaSession.setFlags(
                    MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                            MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            );

            mediaSession.setCallback(new MediaSessionCompat.Callback() {
                @Override
                public void onPlay() {
                    Log.d(TAG, "MediaSession: Play solicitado");
                    playPause();
                }

                @Override
                public void onPause() {
                    Log.d(TAG, "MediaSession: Pause solicitado");
                    playPause();
                }

                @Override
                public void onSkipToNext() {
                    Log.d(TAG, "MediaSession: Próxima música solicitada");
                    nextSong();
                }

                @Override
                public void onSkipToPrevious() {
                    Log.d(TAG, "MediaSession: Música anterior solicitada");
                    prevSong();
                }

                @Override
                public void onStop() {
                    Log.d(TAG, "MediaSession: Stop solicitado");
                    stopSelf();
                }

                @Override
                public void onSeekTo(long pos) {
                    Log.d(TAG, "MediaSession: Seek para " + pos);
                    seekTo(pos);
                }
            });

            mediaSession.setActive(true);
            Log.d(TAG, "MediaSession inicializada e ativa");
        } catch (Exception e) {
            Log.e(TAG, "Erro ao inicializar MediaSession: " + e.getMessage());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Reprodução de Música",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Controles de reprodução de música");
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel.setSound(null, null);

            notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "Canal de notificação criado: " + CHANNEL_ID);
            }
        } else {
            notificationManager = getSystemService(NotificationManager.class);
        }
    }

    private void initializePlayer() {
        try {
            player = new ExoPlayer.Builder(this).build();

            // ✅ CORREÇÃO: Configurar modo de repetição CORRETO para transição automática
            updatePlayerRepeatMode();

            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    Log.d(TAG, "Estado do player alterado: " + playbackState);

                    switch (playbackState) {
                        case Player.STATE_ENDED:
                            Log.d(TAG, "Player: Reprodução finalizada - Processando transição automática");
                            handlePlaybackEnded();
                            break;
                        case Player.STATE_READY:
                            Log.d(TAG, "Player: Estado READY");
                            updateMediaSession();
                            updateNotification();
                            break;
                        case Player.STATE_BUFFERING:
                            Log.d(TAG, "Player: Buffering...");
                            break;
                        case Player.STATE_IDLE:
                            Log.d(TAG, "Player: Estado IDLE");
                            break;
                    }
                }

                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    Log.d(TAG, "Player: Estado de reprodução alterado - " + isPlaying);

                    updateMediaSession();
                    updateNotification();

                    if (presenter != null) {
                        presenter.updatePlayPauseState();
                    }

                    if (isPlaying) {
                        startMediaSessionUpdates();
                        promoteToForeground();
                    } else {
                        stopMediaSessionUpdates();
                        checkIfShouldDemoteToBackground();
                    }
                }

                @Override
                public void onPlayerError(com.google.android.exoplayer2.PlaybackException error) {
                    Log.e(TAG, "Erro no player: " + error.getMessage());
                    transitionHandler.postDelayed(() -> {
                        if (currentSong != null) {
                            Log.d(TAG, "Tentando próxima música devido a erro");
                            nextSong();
                        }
                    }, 2000);
                }
            });

            Log.d(TAG, "ExoPlayer inicializado com sucesso");
        } catch (Exception e) {
            Log.e(TAG, "Erro ao inicializar ExoPlayer: " + e.getMessage());
        }
    }

    // Configurar modo de repetição para permitir transição automática
    private void updatePlayerRepeatMode() {
        if (player != null) {
            if (isRepeatMode) {
                player.setRepeatMode(Player.REPEAT_MODE_ONE);
                Log.d(TAG, "Modo repeat: REPEAT_ONE (repetir mesma música)");
            } else {
                // Usar REPEAT_MODE_ALL para transição automática entre músicas
                player.setRepeatMode(Player.REPEAT_MODE_ALL);
                Log.d(TAG, "Modo repeat: REPEAT_ALL (transição automática entre músicas)");
            }
        }
    }

    private void startMediaSessionUpdates() {
        if (progressUpdateRunnable == null) {
            progressUpdateRunnable = new Runnable() {
                @Override
                public void run() {
                    if (player != null && player.isPlaying()) {
                        updateMediaSession();
                        updateNotification();
                    }

                    if (player != null && player.isPlaying()) {
                        progressHandler.postDelayed(this, 1000);
                    }
                }
            };
        }

        progressHandler.post(progressUpdateRunnable);
    }

    private void stopMediaSessionUpdates() {
        if (progressHandler != null && progressUpdateRunnable != null) {
            progressHandler.removeCallbacks(progressUpdateRunnable);
        }
    }

    private void setupAudioFocus() {
        try {
            audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            int result = audioManager.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
            );

            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.d(TAG, "Audio Focus concedido");
            } else {
                Log.w(TAG, "Audio Focus não concedido");
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao configurar Audio Focus: " + e.getMessage());
        }
    }

    private final AudioManager.OnAudioFocusChangeListener audioFocusChangeListener =
            new AudioManager.OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int focusChange) {
                    Log.d(TAG, "Mudança de Audio Focus: " + focusChange);

                    switch (focusChange) {
                        case AudioManager.AUDIOFOCUS_LOSS:
                            Log.d(TAG, "Audio Focus perdido permanentemente");
                            handleAudioFocusLoss();
                            break;

                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                            Log.d(TAG, "Audio Focus perdido temporariamente");
                            handleAudioFocusLossTransient();
                            break;

                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                            Log.d(TAG, "Audio Focus - modo DUCK (reduzir volume)");
                            if (player != null) {
                                player.setVolume(0.3f);
                            }
                            break;

                        case AudioManager.AUDIOFOCUS_GAIN:
                            Log.d(TAG, "Audio Focus recuperado");
                            handleAudioFocusGain();
                            break;
                    }
                }
            };

    // ============================================================
    // CONTROLE DE FOREGROUND/BACKGROUND
    // ============================================================

    public void promoteToForeground() {
        if (!isInForeground) {
            try {
                Notification notification = buildNotification();

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
                } else {
                    startForeground(NOTIFICATION_ID, notification);
                }

                isInForeground = true;
                Log.d(TAG, "PROMOVIDO para Foreground Service (Música tocando)");

            } catch (Exception e) {
                Log.e(TAG, "Erro ao promover para foreground: " + e.getMessage());
            }
        }
    }

    public void demoteToBackground() {
        if (isInForeground && !isPlaying()) {
            try {
                stopForeground(true);
                isInForeground = false;
                Log.d(TAG, "Rebaixado para Background Service (Música parada)");
            } catch (Exception e) {
                Log.e(TAG, "Erro ao rebaixar para background: " + e.getMessage());
            }
        }
    }

    private void checkIfShouldDemoteToBackground() {
        if (!isPlaying() && isInForeground) {
            transitionHandler.postDelayed(() -> {
                if (!isPlaying()) {
                    demoteToBackground();
                }
            }, 5000);
        }
    }

    // ============================================================
    // MANIPULAÇÃO DE AÇÕES
    // ============================================================

    private void handleIncomingAction(String action, Intent intent) {
        if (action == null) return;

        Log.d(TAG, "Processando ação: " + action + ", Bound: " + isBound);

        switch (action) {
            case ACTION_PLAY:
                handlePlayAction(intent);
                promoteToForeground();
                break;
            case ACTION_PAUSE:
                pausePlayback();
                break;
            case ACTION_PLAY_PAUSE:
                playPause();
                break;
            case ACTION_NEXT:
                nextSong();
                break;
            case ACTION_PREV:
                prevSong();
                break;
            case ACTION_STOP:
                stopSelf();
                break;
            default:
                Log.w(TAG, "Ação desconhecida: " + action);
        }

        updateNotification();
    }

    private void handlePlayAction(Intent intent) {
        String path = intent.getStringExtra("path");
        String title = intent.getStringExtra("title");
        String artist = intent.getStringExtra("artist");
        int duration = intent.getIntExtra("duration", 0);

        if (path != null && title != null && artist != null) {
            Song song = new Song(title, artist, path, duration, 0);
            playSong(song);
            Log.d(TAG, "Ação PLAY processada: " + title);
        } else {
            Log.e(TAG, "Dados insuficientes para ação PLAY");
        }
    }

    // ============================================================
    // CONTROLES DE REPRODUÇÃO COM TRANSIÇÃO AUTOMÁTICA
    // ============================================================

    public void playSong(Song song) {
        if (song == null) {
            Log.e(TAG, "Song é nulo. Não é possível reproduzir.");
            return;
        }

        try {
            Log.d(TAG, "Iniciando reprodução: " + song.getTitle());

            stopMediaSessionUpdates();

            lastKnownSong = song;
            wasPlayingBeforeInterruption = true;

            MediaItem mediaItem = MediaItem.fromUri(song.getPath());

            //Se temos uma playlist, configurar TODAS as músicas
            if (playlist != null && !playlist.isEmpty()) {
                player.clearMediaItems();

                // Adicionar todas as músicas da playlist
                for (Song s : playlist) {
                    player.addMediaItem(MediaItem.fromUri(s.getPath()));
                }

                // Definir o índice atual
                currentSongIndex = findSongIndex(song);
                if (currentSongIndex != -1) {
                    player.seekTo(currentSongIndex, 0);
                }
                Log.d(TAG, "Playlist configurada com " + playlist.size() + " músicas, índice atual: " + currentSongIndex);
            } else {
                // Fallback: apenas a música atual
                player.clearMediaItems();
                player.addMediaItem(mediaItem);
            }

            player.prepare();
            player.play();

            currentSong = song;

            promoteToForeground();
            showNotification();

            notifySongChanged(song);

            if (presenter != null) {
                presenter.onSongChanged(song);
            }

            updateMediaSession();
            updateNotification();

            startMediaSessionUpdates();

            Log.d(TAG, "Música reproduzida com sucesso: " + song.getTitle());

        } catch (Exception e) {
            Log.e(TAG, "Erro ao reproduzir música: " + e.getMessage(), e);
            transitionHandler.postDelayed(() -> nextSong(), 2000);
        }
    }

    public void playPause() {
        if (player == null) {
            Log.e(TAG, "Player não inicializado");
            return;
        }

        if (player.isPlaying()) {
            pausePlayback();
        } else {
            startPlayback();
        }
    }

    private void startPlayback() {
        if (player != null) {
            player.play();
            Log.d(TAG, "Reprodução iniciada");

            startMediaSessionUpdates();
            promoteToForeground();
            updateMediaSession();
            updateNotification();
        }
    }

    private void pausePlayback() {
        if (player != null && player.isPlaying()) {
            player.pause();
            Log.d(TAG, "Reprodução pausada");

            stopMediaSessionUpdates();
            updateMediaSession();
            updateNotification();
        }
    }

    public void nextSong() {
        Log.d(TAG, "Solicitando próxima música");

        if (playlist == null || playlist.isEmpty()) {
            Log.w(TAG, "Playlist vazia - não é possível avançar");
            return;
        }

        if (playlist.size() == 1) {
            Log.d(TAG, "Apenas uma música na playlist - repetindo");
            playSong(playlist.get(0));
            return;
        }

        int nextIndex = calculateNextIndex();
        if (nextIndex >= 0 && nextIndex < playlist.size()) {
            Song nextSong = playlist.get(nextIndex);
            Log.d(TAG, "Próxima música: " + nextSong.getTitle() + " - Índice: " + nextIndex);
            playSong(nextSong);
        } else {
            Log.e(TAG, "Índice inválido calculado: " + nextIndex);

            if (!playlist.isEmpty()) {
                Log.d(TAG, "Usando fallback - tocando primeira música");
                playSong(playlist.get(0));
            }
        }
    }

    public void prevSong() {
        Log.d(TAG, "Solicitando música anterior");

        if (playlist == null || playlist.isEmpty()) {
            Log.w(TAG, "Playlist vazia - não é possível voltar");
            return;
        }

        int prevIndex;
        if (currentSongIndex > 0) {
            prevIndex = currentSongIndex - 1;
        } else {
            prevIndex = playlist.size() - 1;
        }

        if (prevIndex >= 0 && prevIndex < playlist.size()) {
            Song prevSong = playlist.get(prevIndex);
            Log.d(TAG, "Música anterior: " + prevSong.getTitle());
            playSong(prevSong);
        }
    }

    private int calculateNextIndex() {
        if (isShuffleMode) {
            int newIndex;
            do {
                newIndex = (int) (Math.random() * playlist.size());
            } while (newIndex == currentSongIndex && playlist.size() > 1);
            return newIndex;
        } else {
            return (currentSongIndex + 1) % playlist.size();
        }
    }

    //  Método para lidar com o término da reprodução
    private void handlePlaybackEnded() {
        Log.d(TAG, "handlePlaybackEnded - Repeat: " + isRepeatMode + ", Shuffle: " + isShuffleMode);

        notifyPlaybackCompleted();

        if (isRepeatMode) {
            Log.d(TAG, "Modo repeat ativo - repetindo mesma música");
            // Para repeat, o ExoPlayer já cuida da repetição automática
            // devido ao REPEAT_MODE_ONE configurado
        } else {
            Log.d(TAG, "Modo normal - ExoPlayer cuidará da transição automática");
            // ✅ CORREÇÃO: Com REPEAT_MODE_ALL, o ExoPlayer faz transição automática
            // Atualizamos nosso índice atual para refletir a próxima música
            if (playlist != null && currentSongIndex >= 0) {
                int nextIndex = calculateNextIndex();
                if (nextIndex >= 0 && nextIndex < playlist.size()) {
                    currentSongIndex = nextIndex;
                    currentSong = playlist.get(nextIndex);
                    notifySongChanged(currentSong);
                    Log.d(TAG, "Transição automática para: " + currentSong.getTitle());
                }
            }
        }
    }

    // ============================================================
    // NOTIFICAÇÃO E MEDIASESSION
    // ============================================================

    private Notification buildNotification() {
        Intent appIntent = new Intent(this, MusicPlayerActivity.class);
        appIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, appIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        PendingIntent prevPending = createActionPendingIntent(ACTION_PREV, 1);
        PendingIntent playPausePending = createActionPendingIntent(ACTION_PLAY_PAUSE, 2);
        PendingIntent nextPending = createActionPendingIntent(ACTION_NEXT, 3);

        int playPauseIcon = isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play_arrow;
        String playPauseText = isPlaying() ? "Pausar" : "Tocar";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_lm_capa_placeholder)
                .setLargeIcon(getAlbumArtBitmap())
                .setContentTitle(currentSong != null ? currentSong.getTitle() : "Music Player")
                .setContentText(currentSong != null ? currentSong.getArtist() : "Nenhuma música tocando")
                .setContentIntent(contentIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setAutoCancel(false)
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(0, 1, 2)
                        .setMediaSession(mediaSession.getSessionToken()))
                .addAction(R.drawable.ic_skip_previous, "Anterior", prevPending)
                .addAction(playPauseIcon, playPauseText, playPausePending)
                .addAction(R.drawable.ic_skip_next, "Próxima", nextPending);

        return builder.build();
    }

    private PendingIntent createActionPendingIntent(String action, int requestCode) {
        Intent intent = new Intent(this, MusicService.class);
        intent.setAction(action);
        return PendingIntent.getService(this, requestCode, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Bitmap getAlbumArtBitmap() {
        try {
            return BitmapFactory.decodeResource(getResources(), R.drawable.ic_lm_capa_placeholder);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao carregar artwork: " + e.getMessage());
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        }
    }

    private void updateMediaSession() {
        if (mediaSession == null) {
            Log.w(TAG, "MediaSession é nula - não é possível atualizar");
            return;
        }

        try {
            MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE,
                            currentSong != null ? currentSong.getTitle() : "Sem título")
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST,
                            currentSong != null ? currentSong.getArtist() : "Artista desconhecido")
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, getDuration());

            Bitmap albumArt = getAlbumArtBitmap();
            if (albumArt != null) {
                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt);
            }

            mediaSession.setMetadata(metadataBuilder.build());

            int state;
            if (player == null) {
                state = PlaybackStateCompat.STATE_NONE;
            } else if (player.isPlaying()) {
                state = PlaybackStateCompat.STATE_PLAYING;
            } else {
                state = PlaybackStateCompat.STATE_PAUSED;
            }

            long actions = PlaybackStateCompat.ACTION_PLAY |
                    PlaybackStateCompat.ACTION_PAUSE |
                    PlaybackStateCompat.ACTION_PLAY_PAUSE |
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                    PlaybackStateCompat.ACTION_SEEK_TO |
                    PlaybackStateCompat.ACTION_STOP;

            PlaybackStateCompat playbackState = new PlaybackStateCompat.Builder()
                    .setActions(actions)
                    .setState(state, getCurrentPosition(), 1.0f)
                    .build();

            mediaSession.setPlaybackState(playbackState);

            Log.d(TAG, "MediaSession ATUALIZADA - Estado: " + state +
                    ", Posição: " + getCurrentPosition() + "ms, Música: " +
                    (currentSong != null ? currentSong.getTitle() : "Nenhuma"));

        } catch (Exception e) {
            Log.e(TAG, "Erro ao atualizar MediaSession: " + e.getMessage());
        }
    }

    private void showNotification() {
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification());
            Log.d(TAG, "Notificação mostrada");
        }
    }

    private void updateNotification() {
        if (notificationManager != null && player != null) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification());
            Log.d(TAG, "Notificação atualizada");
        }
    }

    private void hideNotification() {
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
            Log.d(TAG, "Notificação ocultada");
        }
    }

    // ============================================================
    // MANIPULAÇÃO DE AUDIO FOCUS
    // ============================================================

    private void handleAudioFocusLoss() {
        if (isPlaying()) {
            wasPlayingBeforeInterruption = true;
            playbackPositionBeforeInterruption = getCurrentPosition();
            lastKnownSong = currentSong;
            pausePlayback();
        }
    }

    private void handleAudioFocusLossTransient() {
        if (isPlaying()) {
            wasPlayingBeforeInterruption = true;
            playbackPositionBeforeInterruption = getCurrentPosition();
            lastKnownSong = currentSong;
            pausePlayback();
        }
    }

    private void handleAudioFocusGain() {
        if (player != null) {
            player.setVolume(1.0f);

            if (wasPlayingBeforeInterruption && !isPlaying() && lastKnownSong != null) {
                Log.d(TAG, "Restaurando reprodução interrompida");
                transitionHandler.postDelayed(() -> {
                    if (lastKnownSong != null) {
                        playSong(lastKnownSong);
                        if (playbackPositionBeforeInterruption > 0) {
                            seekTo(playbackPositionBeforeInterruption);
                        }
                        wasPlayingBeforeInterruption = false;
                        playbackPositionBeforeInterruption = 0;
                    }
                }, 1000);
            }
        }
    }

    // ============================================================
    // MÉTODOS PÚBLICOS PARA COMUNICAÇÃO
    // ============================================================

    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    public long getCurrentPosition() {
        return player != null ? player.getCurrentPosition() : 0;
    }

    public long getDuration() {
        return player != null ? player.getDuration() : 0;
    }

    public void seekTo(long position) {
        if (player != null) {
            player.seekTo(position);
            updateMediaSession();
            Log.d(TAG, "Seek para: " + position + "ms");
        }
    }

    public Song getCurrentSong() {
        return currentSong;
    }

    public void setPlaylist(List<Song> playlist) {
        this.playlist = playlist;
        Log.d(TAG, "Playlist definida com " + (playlist != null ? playlist.size() : 0) + " músicas");
    }

    public void setCurrentSongIndex(int index) {
        this.currentSongIndex = index;
        Log.d(TAG, "Índice atual definido: " + index);
    }

    public void setShuffleMode(boolean shuffle) {
        this.isShuffleMode = shuffle;
        Log.d(TAG, "Modo shuffle: " + (shuffle ? "LIGADO" : "DESLIGADO"));
        updateMediaSession();
        updateNotification();
    }

    public void setRepeatMode(boolean repeat) {
        this.isRepeatMode = repeat;
        Log.d(TAG, "Modo repeat: " + (repeat ? "LIGADO" : "DESLIGADO"));
        updatePlayerRepeatMode(); // ✅ ATUALIZAR MODO DO PLAYER
        updateMediaSession();
        updateNotification();
    }

    public void setPresenter(MusicPresenter presenter) {
        this.presenter = presenter;
        Log.d(TAG, "Presenter definido no serviço");
    }

    public ExoPlayer getPlayer() {
        return player;
    }

    public boolean isInForeground() {
        return isInForeground;
    }

    public boolean isBound() {
        return isBound;
    }

    // ============================================================
    // UTILITÁRIOS
    // ============================================================

    private int findSongIndex(Song song) {
        if (playlist == null || song == null) return -1;
        for (int i = 0; i < playlist.size(); i++) {
            Song s = playlist.get(i);
            if (s.getTitle().equals(song.getTitle()) && s.getArtist().equals(song.getArtist())) {
                return i;
            }
        }
        return -1;
    }

    // ============================================================
    // LIMPEZA DE RECURSOS
    // ============================================================

    private void releaseAudioFocus() {
        if (audioManager != null) {
            audioManager.abandonAudioFocus(audioFocusChangeListener);
            Log.d(TAG, "Audio Focus abandonado");
        }
    }

    private void releaseMediaSession() {
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
            Log.d(TAG, "MediaSession liberada");
        }
    }

    private void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
            Log.d(TAG, "Player liberado");
        }
    }
}