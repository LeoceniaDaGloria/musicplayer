package com.example.musicplayer.view;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.graphics.Bitmap;
import android.widget.Toast;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.example.musicplayer.R;
import com.example.musicplayer.model.Playlist;
import com.example.musicplayer.model.Song;
import com.example.musicplayer.presenter.MusicPresenter;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity principal do player de música
 * Responsável pela interface de reprodução com controles completos
 * VERSÃO CORRIGIDA: Remove MediaControllerCompat problemático e mantém funcionalidades
 */
public class MusicPlayerActivity extends AppCompatActivity implements MusicView, MusicPresenter.OnMusicOperationListener, MusicPresenter.OnSongChangeListener {
    private static final String TAG = "MusicPlayerActivity";

    private MusicPresenter presenter;
    private Toolbar toolbar;

    // Views da interface do player
    private ImageView albumCover, playPauseButton, prevButton, nextButton,
            shuffleButton, repeatButton;
    private TextView songTitle, artistName, tvTempoAtual, tvDuracaoTotal;
    private SeekBar songProgress;

    // Botão de favorito
    private ImageView favoriteButton;

    // Animação de rotação da capa do álbum
    private ObjectAnimator rotationAnimator;

    // Música atual sendo reproduzida
    private Song currentSong;

    // ✅ VARIÁVEL PARA CONTROLE DE SINCRONIZAÇÃO SIMPLES
    private String currentDisplayedTitle = "";

    // ============================================================
    // CICLO DE VIDA PRINCIPAL
    // ============================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music_player);

        Log.d(TAG, "🎵 MusicPlayerActivity criada");

        // Obtém a música da intent de origem
        getSongFromIntent();

        // Configura a interface
        setupToolbar();
        initializeViews();
        setupListeners();
        setupAlbumArtAnimation();

        // Inicializa o presenter para controle de reprodução
        presenter = new MusicPresenter(this, this, this);

        // ✅ REGISTRAR LISTENER PARA ATUALIZAÇÕES DE MÚSICA
        presenter.registerSongChangeListener(this);

        // Configurar listener para operações
        presenter.setOperationListener(this);

        // VERIFICAR SE VEIO DE UMA PLAYLIST (BOTÕES REPRODUZIR/ALEATÓRIA)
        Intent intent = getIntent();
        long[] songIds = intent.getLongArrayExtra("song_ids");
        int currentPosition = intent.getIntExtra("current_position", 0);

        if (songIds != null && songIds.length > 0) {
            // ✅ MODO PLAYLIST - CARREGAR PELOS IDs
            Log.d(TAG, "🎵 Recebida playlist: " + songIds.length + " músicas, posição: " + currentPosition);
            loadSongsFromIds(songIds, currentPosition);
        } else if (currentSong != null) {
            // ✅ MODO NORMAL - MÚSICA INDIVIDUAL
            presenter.playSong(currentSong);
            checkInitialFavoriteState();

            // Pequeno delay para garantir que o service está pronto
            new android.os.Handler().postDelayed(() -> {
                loadCurrentSongAlbumArt();
            }, 300);

            startAlbumRotation();
        }

        // Verifica se já há uma música tocando do service
        checkIfAlreadyPlaying();

        // ✅ INICIAR SISTEMA SIMPLES DE ATUALIZAÇÃO
        startSimpleUpdateSystem();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "🔄 onResume - Atualizando interface");

        // ✅ REGISTRAR LISTENER NOVAMENTE
        if (presenter != null) {
            presenter.registerSongChangeListener(this);
        }

        // ✅ FORÇAR ATUALIZAÇÃO DA CAPA
        new android.os.Handler().postDelayed(() -> {
            loadAlbumArtFromService();
        }, 300);

        // Atualiza estado do favorito
        if (currentSong != null) {
            checkInitialFavoriteState();
        }

        // Retoma animação se a música estiver tocando
        if (presenter != null && presenter.isPlaying()) {
            resumeAlbumRotation();
        }

        // ✅ ATUALIZAR UI COM ESTADO ATUAL
        updateUIFromService();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "⏸️ onPause - Pausando animação");
        stopAlbumRotation();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "💀 onDestroy - Liberando recursos");

        // ✅ REMOVER LISTENER APENAS NO DESTROY
        if (presenter != null) {
            presenter.unregisterSongChangeListener(this);
            presenter.onDestroy();
        }

        resetAlbumRotation();
    }

    // ============================================================
    // ✅ SISTEMA SIMPLES DE ATUALIZAÇÃO (FUNCIONAVA ANTES)
    // ============================================================

    /**
     * ✅ SISTEMA SIMPLES: Atualização periódica a partir do Service
     * Esta era a abordagem que funcionava antes
     */
    private void startSimpleUpdateSystem() {
        // Atualização a cada segundo para manter a UI sincronizada
        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                updateUIFromService();
                new android.os.Handler().postDelayed(this, 1000);
            }
        }, 1000);
    }

    /**
     * ✅ ATUALIZA UI A PARTIR DO SERVICE - MÉTODO QUE FUNCIONAVA
     */
    private void updateUIFromService() {
        if (presenter != null && presenter.getMusicService() != null) {
            Song currentSong = presenter.getMusicService().getCurrentSong();
            if (currentSong != null) {
                // ✅ VERIFICAÇÃO SIMPLES: Só atualiza se a música mudou
                if (!currentSong.getTitle().equals(currentDisplayedTitle)) {
                    currentDisplayedTitle = currentSong.getTitle();

                    // ATUALIZAR INFORMAÇÕES DA MÚSICA
                    updateSongInfo(currentSong.getTitle(), currentSong.getArtist());
                    updateFavoriteIcon(currentSong.isFavorite());

                    // ATUALIZAR CAPA
                    presenter.updateSongInfoWithCover(currentSong);

                    Log.d(TAG, "🔄 UI atualizada do Service: " + currentSong.getTitle());
                }

                // ✅ ATUALIZAR CONTROLES DE REPRODUÇÃO
                updatePlayPauseIcon(presenter.getMusicService().isPlaying());
                updateProgress(
                        (int) presenter.getMusicService().getCurrentPosition(),
                        (int) presenter.getMusicService().getDuration()
                );

                // ✅ ATUALIZAR MODOS DE REPRODUÇÃO
                updateShuffleIcon(presenter.isShuffleMode());
                updateRepeatIcon(presenter.isRepeatMode());
            }
        }
    }

    // ============================================================
    // CONFIGURAÇÃO INICIAL DA INTERFACE
    // ============================================================

    /**
     * Extrai os dados da música da intent que iniciou esta activity
     */
    private void getSongFromIntent() {
        try {
            String path = getIntent().getStringExtra("song_path");
            String title = getIntent().getStringExtra("song_title");
            String artist = getIntent().getStringExtra("song_artist");
            int duration = getIntent().getIntExtra("song_duration", 0);

            currentSong = new Song(title, artist, path, duration, 0);
            currentDisplayedTitle = title; // ✅ INICIALIZAR CONTROLE
            Log.d(TAG, "🎵 Música recebida da intent: " + title + " - " + artist);
        } catch (Exception e) {
            Log.e(TAG, "❌ Erro ao obter música da intent: " + e.getMessage());
        }
    }

    /**
     * Configura a toolbar com botão de voltar e título
     */
    private void setupToolbar() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle("Tocando Agora");
        }

        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    /**
     * Inicializa todas as views da interface
     */
    private void initializeViews() {
        albumCover = findViewById(R.id.album_cover);
        songTitle = findViewById(R.id.song_title);
        artistName = findViewById(R.id.artist_name);
        tvTempoAtual = findViewById(R.id.tv_tempo_atual);
        tvDuracaoTotal = findViewById(R.id.tv_duracao_total);
        playPauseButton = findViewById(R.id.playPauseButton);
        prevButton = findViewById(R.id.prevButton);
        nextButton = findViewById(R.id.nextButton);
        shuffleButton = findViewById(R.id.shuffleButton);
        repeatButton = findViewById(R.id.repeatButton);
        songProgress = findViewById(R.id.song_progress);

        favoriteButton = findViewById(R.id.favorite_button);

        // Carrega a capa padrão com formato circular
        try {
            RequestOptions requestOptions = new RequestOptions()
                    .circleCrop()
                    .override(400, 400);

            Glide.with(this)
                    .load(R.drawable.ic_lm_capa_placeholder)
                    .apply(requestOptions)
                    .into(albumCover);
        } catch (Exception e) {
            Log.e(TAG, "❌ Erro ao carregar capa do álbum: " + e.getMessage());
            albumCover.setImageResource(R.drawable.ic_lm_capa_placeholder);
        }

        // Configura estados iniciais dos botões
        updatePlayPauseIcon(false);
        updateShuffleIcon(false);
        updateRepeatIcon(false);
    }

    /**
     * Configura os listeners para todos os botões e controles
     */
    private void setupListeners() {
        // Controles básicos de reprodução
        playPauseButton.setOnClickListener(v -> {
            Log.d(TAG, "🎵 Play/Pause clicado");
            presenter.playPause();
        });

        prevButton.setOnClickListener(v -> {
            Log.d(TAG, "⏮️ Anterior clicado");
            presenter.prevSong();
        });

        nextButton.setOnClickListener(v -> {
            Log.d(TAG, "⏭️ Próxima clicado");
            presenter.nextSong();
        });

        // Controles de modo de reprodução
        shuffleButton.setOnClickListener(v -> {
            if (presenter != null) {
                presenter.toggleShuffle();
                updateShuffleIcon(presenter.isShuffleMode());
                Log.d(TAG, "🔀 Shuffle: " + presenter.isShuffleMode());
            }
        });

        repeatButton.setOnClickListener(v -> {
            if (presenter != null) {
                presenter.toggleRepeat();
                updateRepeatIcon(presenter.isRepeatMode());
                Log.d(TAG, "🔁 Repeat: " + presenter.isRepeatMode());
            }
        });

        // Botão de favorito
        if (favoriteButton != null) {
            favoriteButton.setOnClickListener(v -> toggleFavorite());
        } else {
            Log.w(TAG, "⚠️ Botão favorito não encontrado no layout");
        }

        // SeekBar para controle de progresso
        songProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && presenter != null) {
                    presenter.seekTo(progress);
                    Log.d(TAG, "🎚️ Seek para: " + progress + "ms");
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                Log.d(TAG, "👆 Iniciando seek");
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Log.d(TAG, "👆 Parando seek");
            }
        });
    }

    // ============================================================
    // CARREGAMENTO DE MÚSICAS E PLAYLISTS
    // ============================================================

    /**
     * ✅ MÉTODO: Carrega músicas a partir de IDs recebidos da playlist
     */
    private void loadSongsFromIds(long[] songIds, int startPosition) {
        Log.d(TAG, "🎵 Carregando " + songIds.length + " músicas da playlist...");

        new Thread(() -> {
            List<Song> loadedSongs = new ArrayList<>();

            // ✅ CARREGAR CADA MÚSICA DO BANCO PELO ID
            for (long songId : songIds) {
                Song song = presenter.getSongById(songId);
                if (song != null) {
                    loadedSongs.add(song);
                    Log.d(TAG, "✅ Carregada: " + song.getTitle() + " (ID: " + songId + ")");
                } else {
                    Log.w(TAG, "⚠️ Música não encontrada: ID " + songId);
                }
            }

            runOnUiThread(() -> {
                if (!loadedSongs.isEmpty()) {
                    // ✅ INICIAR REPRODUÇÃO DA PLAYLIST
                    presenter.playPlaylist(loadedSongs, startPosition);

                    // ✅ ATUALIZAR UI COM A PRIMEIRA MÚSICA
                    if (startPosition < loadedSongs.size()) {
                        currentSong = loadedSongs.get(startPosition);
                        currentDisplayedTitle = currentSong.getTitle();

                        updateSongInfo(currentSong.getTitle(), currentSong.getArtist());
                        updateFavoriteIcon(currentSong.isFavorite());
                        loadCurrentSongAlbumArt();
                    }

                    startAlbumRotation();
                    Toast.makeText(this, "🎵 Playlist: " + loadedSongs.size() + " músicas", Toast.LENGTH_SHORT).show();

                } else {
                    Toast.makeText(this, "❌ Erro ao carregar playlist", Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
        }).start();
    }

    /**
     * Verifica se já existe uma música em reprodução vinda do service
     */
    private void checkIfAlreadyPlaying() {
        new android.os.Handler().postDelayed(() -> {
            if (presenter != null && presenter.isPlaying()) {
                Log.d(TAG, "🎵 Música já está tocando - iniciando animação");
                startAlbumRotation();

                // ✅ ATUALIZAR UI COM ESTADO ATUAL
                if (presenter.getMusicService() != null && presenter.getMusicService().getCurrentSong() != null) {
                    Song currentPlaying = presenter.getMusicService().getCurrentSong();
                    onSongChanged(currentPlaying);
                }
            }
        }, 500);
    }

    // ============================================================
    // ANIMAÇÃO DA CAPA DO ÁLBUM
    // ============================================================

    /**
     * Configura a animação de rotação da capa do álbum
     */
    private void setupAlbumArtAnimation() {
        runOnUiThread(() -> {
            if (albumCover != null) {
                // CANCELAR ANIMAÇÃO EXISTENTE
                if (rotationAnimator != null) {
                    rotationAnimator.cancel();
                    rotationAnimator = null;
                }

                // CONFIGURAÇÃO ROBUSTA DA ANIMAÇÃO
                rotationAnimator = ObjectAnimator.ofFloat(albumCover, "rotation", 0f, 360f);
                rotationAnimator.setDuration(15000);
                rotationAnimator.setRepeatCount(ObjectAnimator.INFINITE);
                rotationAnimator.setInterpolator(new LinearInterpolator());
                rotationAnimator.setAutoCancel(true);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    albumCover.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                }

                Log.d(TAG, "🔄 Animação configurada");
            }
        });
    }

    /**
     * Inicia a animação de rotação da capa
     */
    private void startAlbumRotation() {
        if (rotationAnimator != null && !rotationAnimator.isRunning()) {
            rotationAnimator.start();
            Log.d(TAG, "🎡 Animação de rotação INICIADA");
        }
    }

    /**
     * Pausa a animação de rotação da capa
     */
    private void stopAlbumRotation() {
        if (rotationAnimator != null && rotationAnimator.isRunning()) {
            rotationAnimator.pause();
            Log.d(TAG, "⏸️ Animação de rotação PAUSADA");
        }
    }

    /**
     * Retoma a animação de rotação da capa
     */
    private void resumeAlbumRotation() {
        if (rotationAnimator != null && rotationAnimator.isPaused()) {
            rotationAnimator.resume();
            Log.d(TAG, "▶️ Animação de rotação RETOMADA");
        }
    }

    /**
     * Para e reseta a animação de rotação da capa
     */
    private void resetAlbumRotation() {
        if (rotationAnimator != null) {
            rotationAnimator.cancel();
            if (albumCover != null) {
                albumCover.setRotation(0f);
            }
            Log.d(TAG, "🔄 Animação de rotação RESETADA");
        }
    }

    /**
     * Detecta mudanças de tema (claro/escuro) e recria a animação
     */
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        boolean isDarkTheme = (newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;

        Log.d(TAG, "🎨 Tema alterado: " + (isDarkTheme ? "ESCURO" : "CLARO"));

        runOnUiThread(() -> {
            if (rotationAnimator != null && rotationAnimator.isRunning()) {
                Log.d(TAG, "🔄 Recriando animação para o novo tema");
                resetAlbumRotation();
                setupAlbumArtAnimation();
                startAlbumRotation();
            }
        });
    }

    // ============================================================
    // CARREGAMENTO DE CAPAS E IMAGENS
    // ============================================================

    /**
     * CARREGA CAPA REAL - MESMO MÉTODO DA MAINACTIVITY
     */
    private void loadAlbumArtFromService() {
        runOnUiThread(() -> {
            try {
                if (presenter != null) {
                    if (presenter.getMusicService() != null) {
                        Song currentPlaying = presenter.getMusicService().getCurrentSong();
                        if (currentPlaying != null) {
                            presenter.updateSongInfoWithCover(currentPlaying);
                            Log.d(TAG, "✅ Capa solicitada via presenter: " + currentPlaying.getTitle());
                        } else if (currentSong != null) {
                            presenter.updateSongInfoWithCover(currentSong);
                            Log.d(TAG, "✅ Capa solicitada via música da intent: " + currentSong.getTitle());
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ Erro ao carregar capa via presenter: " + e.getMessage());
                loadCurrentSongAlbumArt();
            }
        });
    }

    /**
     * Carrega a capa real do álbum da música atual
     */
    private void loadCurrentSongAlbumArt() {
        runOnUiThread(() -> {
            try {
                if (presenter != null) {
                    if (presenter.getMusicService() != null) {
                        Song serviceSong = presenter.getMusicService().getCurrentSong();
                        if (serviceSong != null) {
                            presenter.updateSongInfoWithCover(serviceSong);
                            Log.d(TAG, "✅ Capa solicitada via service: " + serviceSong.getTitle());
                            return;
                        }
                    }

                    if (currentSong != null) {
                        RequestOptions requestOptions = new RequestOptions()
                                .circleCrop()
                                .override(400, 400);

                        Glide.with(this)
                                .load(currentSong.getAlbumArtUri())
                                .apply(requestOptions)
                                .error(
                                        Glide.with(this)
                                                .load(R.drawable.ic_lm_capa_placeholder)
                                                .apply(requestOptions)
                                )
                                .into(albumCover);

                        Log.d(TAG, "✅ Capa carregada da URI: " + currentSong.getTitle());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ Erro ao carregar capa: " + e.getMessage());
                albumCover.setImageResource(R.drawable.ic_lm_capa_placeholder);
            }
        });
    }

    /**
     * ✅ MÉTODO PARA CARREGAR CAPA PADRÃO
     */
    private void loadDefaultAlbumArt() {
        try {
            RequestOptions requestOptions = new RequestOptions()
                    .circleCrop()
                    .override(400, 400);

            Glide.with(this)
                    .load(R.drawable.ic_lm_capa_placeholder)
                    .apply(requestOptions)
                    .into(albumCover);

        } catch (Exception e) {
            Log.e(TAG, "❌ Erro ao carregar placeholder: " + e.getMessage());
            albumCover.setImageResource(R.drawable.ic_lm_capa_placeholder);
        }
    }

    // ============================================================
    // CONTROLES DE FAVORITO
    // ============================================================

    /**
     * Verifica o estado inicial do favorito no banco de dados
     */
    private void checkInitialFavoriteState() {
        if (currentSong != null) {
            new Thread(() -> {
                try {
                    boolean isFavorite = currentSong.isFavorite();

                    runOnUiThread(() -> {
                        updateFavoriteIcon(isFavorite);
                        Log.d(TAG, "⭐ Estado inicial do favorito: " + currentSong.getTitle() + " - " + isFavorite);
                    });
                } catch (Exception e) {
                    Log.e(TAG, "❌ Erro ao verificar estado do favorito: " + e.getMessage());
                }
            }).start();
        }
    }

    /**
     * Alterna o estado de favorito da música atual
     */
    private void toggleFavorite() {
        if (currentSong != null && presenter != null) {
            boolean currentState = currentSong.isFavorite();
            boolean newFavoriteState = !currentState;

            updateFavoriteIcon(newFavoriteState);
            currentSong.setFavorite(newFavoriteState);
            presenter.toggleFavorite(currentSong);

            Log.d(TAG, "⭐ Favorito alternado: " + currentSong.getTitle() +
                    " - Estado anterior: " + currentState +
                    " - Novo estado: " + newFavoriteState);

            showFavoriteToast(newFavoriteState);
        }
    }

    /**
     * Exibe toast confirmando a ação de favorito
     */
    private void showFavoriteToast(boolean isFavorite) {
        String message = isFavorite ?
                "⭐ Adicionado aos Favoritos" :
                "❌ Removido dos Favoritos";

        runOnUiThread(() -> {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * Atualiza visualmente o ícone de favorito
     */
    private void updateFavoriteIcon(boolean isFavorite) {
        if (favoriteButton != null) {
            try {
                int iconResource = isFavorite ? R.drawable.ic_star : R.drawable.ic_star_outline;
                favoriteButton.setImageResource(iconResource);

                favoriteButton.animate()
                        .scaleX(1.3f).scaleY(1.3f).setDuration(150)
                        .withEndAction(() ->
                                favoriteButton.animate()
                                        .scaleX(1f).scaleY(1f).setDuration(150)
                                        .start()
                        ).start();

            } catch (Exception e) {
                Log.e(TAG, "❌ Erro ao atualizar ícone de favorito: " + e.getMessage());
                favoriteButton.setImageResource(isFavorite ?
                        android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off);
            }
        }
    }

    // ============================================================
    // ATUALIZAÇÃO DE UI E CONTROLES VISUAIS
    // ============================================================

    /**
     * Atualiza o ícone do botão shuffle
     */
    private void updateShuffleIcon(boolean isShuffleOn) {
        if (shuffleButton != null) {
            try {
                shuffleButton.setImageResource(isShuffleOn ?
                        R.drawable.ic_shuffle_on : R.drawable.ic_shuffle);
                shuffleButton.setAlpha(isShuffleOn ? 1.0f : 0.6f);
            } catch (Exception e) {
                Log.e(TAG, "❌ Erro ao atualizar ícone do shuffle: " + e.getMessage());
            }
        }
    }

    /**
     * Atualiza o ícone do botão repeat
     */
    private void updateRepeatIcon(boolean isRepeatOn) {
        if (repeatButton != null) {
            try {
                repeatButton.setImageResource(isRepeatOn ?
                        R.drawable.ic_repeat_on : R.drawable.ic_repeat);
                repeatButton.setAlpha(isRepeatOn ? 1.0f : 0.6f);
            } catch (Exception e) {
                Log.e(TAG, "❌ Erro ao atualizar ícone do repeat: " + e.getMessage());
            }
        }
    }

    /**
     * Formata tempo em milissegundos para formato MM:SS
     */
    private String formatTime(int ms) {
        int seconds = (ms / 1000) % 60;
        int minutes = (ms / 1000) / 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    // ============================================================
    // ✅ IMPLEMENTAÇÃO DO OnSongChangeListener (ESSENCIAL)
    // ============================================================

    /**
     * ✅ MÉTODO CRÍTICO: Chamado quando a música é alterada durante a reprodução
     */
    @Override
    public void onSongChanged(Song song) {
        Log.d(TAG, "🔄 onSongChanged chamado: " + (song != null ? song.getTitle() : "null"));

        runOnUiThread(() -> {
            if (song != null) {
                // ✅ ATUALIZAR VARIÁVEIS DE CONTROLE
                currentSong = song;
                currentDisplayedTitle = song.getTitle();

                updateSongInfo(song.getTitle(), song.getArtist());
                updatePlayPauseIcon(true);
                updateFavoriteIcon(song.isFavorite());

                loadCurrentSongAlbumArt();
                startAlbumRotation();

                Log.d(TAG, "🎵 Música alterada na UI via callback: " + song.getTitle());
            }
        });
    }

    /**
     * ✅ MÉTODO CRÍTICO: Chamado quando o estado de reprodução muda
     */
    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        Log.d(TAG, "▶️ onPlaybackStateChanged: " + isPlaying);

        runOnUiThread(() -> {
            updatePlayPauseIcon(isPlaying);

            if (isPlaying) {
                startAlbumRotation();
            } else {
                stopAlbumRotation();
            }
        });
    }

    // ============================================================
    // IMPLEMENTAÇÃO DA INTERFACE MusicView
    // ============================================================

    @Override
    public void updateSongInfo(String title, String artist) {
        runOnUiThread(() -> {
            if (songTitle != null) songTitle.setText(title);
            if (artistName != null) artistName.setText(artist);

            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(title);
            }

            Log.d(TAG, "📝 Info atualizada: " + title + " - " + artist);
        });
    }

    @Override
    public void updatePlayPauseIcon(boolean isPlaying) {
        runOnUiThread(() -> {
            if (playPauseButton != null) {
                try {
                    playPauseButton.setImageResource(isPlaying ?
                            R.drawable.ic_pause : R.drawable.ic_play_arrow);

                    playPauseButton.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150)
                            .withEndAction(() -> playPauseButton.animate().scaleX(1f).scaleY(1f).setDuration(150));

                    Log.d(TAG, "⏯️ Play/Pause atualizado: " + (isPlaying ? "PAUSE" : "PLAY"));

                } catch (Exception e) {
                    Log.e(TAG, "❌ Erro ao atualizar ícone play/pause: " + e.getMessage());
                }
            }
        });
    }

    @Override
    public void updateProgress(int progress, int duration) {
        runOnUiThread(() -> {
            if (songProgress != null) {
                songProgress.setMax(duration);
                songProgress.setProgress(progress);
            }
            if (tvTempoAtual != null) tvTempoAtual.setText(formatTime(progress));
            if (tvDuracaoTotal != null) tvDuracaoTotal.setText(formatTime(duration));
        });
    }

    @Override
    public void updateAlbumArt(Bitmap albumArt) {
        runOnUiThread(() -> {
            Log.d(TAG, "🎨 updateAlbumArt chamado - Bitmap: " + (albumArt != null ? "VÁLIDO" : "NULL"));

            if (albumCover != null) {
                try {
                    if (albumArt != null) {
                        RequestOptions requestOptions = new RequestOptions()
                                .circleCrop()
                                .override(400, 400);

                        Glide.with(this)
                                .load(albumArt)
                                .apply(requestOptions)
                                .into(albumCover);

                        Log.d(TAG, "✅ Capa real carregada na UI");
                    } else {
                        loadDefaultAlbumArt();
                        Log.d(TAG, "🖼️ Placeholder carregado");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "❌ Erro ao carregar capa na UI: " + e.getMessage());
                    loadDefaultAlbumArt();
                }
            }
        });
    }

    @Override
    public void updateSongList(List<Song> songs) {
        if (presenter != null) {
            presenter.setServicePlaylist(songs);
            Log.d(TAG, "🎵 Playlist configurada no service: " + songs.size() + " músicas");
        }
    }

    // ============================================================
    // IMPLEMENTAÇÃO DO OnMusicOperationListener
    // ============================================================

    @Override
    public void onSongRenamed(Song song) {
        if (currentSong != null && currentSong.getId() == song.getId()) {
            runOnUiThread(() -> {
                currentSong = song;
                updateSongInfo(song.getTitle(), song.getArtist());
                Toast.makeText(this, "✏️ Música renomeada", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "✏️ Música atual renomeada: " + song.getTitle());
            });
        }
    }

    @Override
    public void onSongDeleted(Song song) {
        if (currentSong != null && currentSong.getId() == song.getId()) {
            runOnUiThread(() -> {
                Toast.makeText(this, "🗑️ Música atual foi excluída", Toast.LENGTH_SHORT).show();
                onBackPressed();
                Log.d(TAG, "🗑️ Música atual excluída, voltando para lista: " + song.getTitle());
            });
        }
    }

    @Override
    public void onCoverChanged() {
        runOnUiThread(() -> {
            loadCurrentSongAlbumArt();
            Toast.makeText(this, "🖼️ Capa atualizada", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "🖼️ Capa alterada - recarregando visualização");
        });
    }

    // ============================================================
    // MENU E NAVEGAÇÃO (MANTIDO)
    // ============================================================

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        applyDynamicMenuColors(menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();

        if (searchView != null) {
            // [Configuração do SearchView mantida igual]
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_favoritas) {
            navigateTo(FavoritasActivity.class);
            return true;
        } else if (id == R.id.action_descarregadas) {
            navigateTo(DescarregadasActivity.class);
            return true;
        } else if (id == R.id.action_artistas) {
            navigateTo(ArtistsActivity.class);
            return true;
        } else if (id == R.id.action_recognize) {
            navigateTo(ReconhecerMusicaActivity.class);
            return true;
        } else if (id == R.id.action_playlists) {
            navigateTo(PlaylistsActivity.class);
            return true;
        } else if (id == android.R.id.home) {
            onBackPressed();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Navega para outras activities do app
     */
    private void navigateTo(Class<?> activityClass) {
        try {
            if (this.getClass().equals(activityClass)) {
                return;
            }
            Intent intent = new Intent(this, activityClass);
            startActivity(intent);
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        } catch (Exception e) {
            Log.e(TAG, "❌ Erro ao navegar: " + e.getMessage());
        }
    }

    /**
     * Obtém cor baseada no tema atual (claro/escuro)
     */
    private int getColorForTheme(@ColorRes int lightColor, @ColorRes int darkColor) {
        int currentNightMode = getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;

        if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
            return ContextCompat.getColor(this, darkColor);
        } else {
            return ContextCompat.getColor(this, lightColor);
        }
    }

    /**
     * Aplica cores dinâmicas aos itens do menu
     */
    private void applyDynamicMenuColors(Menu menu) {
        try {
            int menuIconColor = getColorForTheme(
                    R.color.icon_primary_light,
                    R.color.icon_primary_dark
            );

            int menuTextColor = getColorForTheme(
                    R.color.text_primary_light,
                    R.color.text_primary_dark
            );

            for (int i = 0; i < menu.size(); i++) {
                MenuItem item = menu.getItem(i);

                if (item.getIcon() != null) {
                    item.getIcon().setColorFilter(menuIconColor, android.graphics.PorterDuff.Mode.SRC_IN);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        item.getIcon().setTint(menuIconColor);
                    }
                }

                SpannableString spanString = new SpannableString(item.getTitle());
                spanString.setSpan(new ForegroundColorSpan(menuTextColor), 0, spanString.length(), 0);
                item.setTitle(spanString);
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Erro ao aplicar cores dinâmicas ao menu: " + e.getMessage());
        }
    }

    // ============================================================
    // MÉTODOS NÃO UTILIZADOS (IMPLEMENTAÇÃO VAZIA)
    // ============================================================

    @Override
    public void updatePlaylistList(List<Playlist> playlists) {}
    @Override
    public void updateArtists(List<String> artists) {}
    @Override
    public void showRecognitionResult(String result) {}
    @Override
    public void requestPermissions() {}
    @Override
    public void playSong(Song song) {}
    @Override
    public void playPause() {}
    @Override
    public void prevSong() {}
    @Override
    public void nextSong() {}
    @Override
    public void toggleShuffle() {}
    @Override
    public void toggleRepeat() {}
    @Override
    public void seekTo(int progress) {}
    @Override
    public void searchSongs(String query) {}
    @Override
    public void createPlaylist() {}
    @Override
    public void recognizeMusic() {}
    @Override
    public void loadPlaylistSongs(Playlist playlist) {}
}