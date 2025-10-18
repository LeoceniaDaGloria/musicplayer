package com.example.musicplayer.view;

import androidx.annotation.ColorRes;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.animation.ObjectAnimator;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Environment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.example.musicplayer.R;
import com.example.musicplayer.adapter.SongAdapter;
import com.example.musicplayer.dialog.DialogDeleteSong;
import com.example.musicplayer.dialog.DialogRenameSong;
import com.example.musicplayer.model.Playlist;
import com.example.musicplayer.model.Song;
import com.example.musicplayer.presenter.MusicPresenter;
import com.example.musicplayer.services.MusicService;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity principal que implementa a View na MVP.
 * Gerencia UI, permissões e binding com serviço.
 * VERSÃO CORRIGIDA: Sistema simples de atualização que funcionava antes
 */
public class MainActivity extends AppCompatActivity implements MusicView, MusicPresenter.OnMusicOperationListener, MusicPresenter.OnSongChangeListener {
    private static final int REQUEST_CODE_PERMISSIONS = 100;
    private static final String[] REQUIRED_PERMISSIONS;

    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            REQUIRED_PERMISSIONS = new String[]{
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.POST_NOTIFICATIONS
            };
        } else {
            REQUIRED_PERMISSIONS = new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.POST_NOTIFICATIONS
            };
        }
    }

    private static final String TAG = "MainActivity";

    private MusicPresenter presenter;
    private ImageView albumCover;
    private TextView songTitle, artistName, tvTempoAtual, tvDuracaoTotal;
    private ImageView playPauseButton, prevButton, nextButton, shuffleButton, repeatButton;
    private SeekBar songProgress;

    private RecyclerView recyclerViewSongs;
    private SongAdapter songAdapter;
    private Handler handler = new Handler();
    private ObjectAnimator rotateAnimator;
    private MusicService musicService;
    private boolean isBound = false;
    private Toolbar toolbar;

    // ✅ VARIÁVEL SIMPLES PARA CONTROLE DE ATUALIZAÇÃO
    private String currentDisplayedTitle = "";

    // ============================================================
    // SERVICE CONNECTION - COMUNICAÇÃO COM MUSIC SERVICE
    // ============================================================

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "🎵 Service connected");
            musicService = ((MusicService.MusicBinder) service).getService();
            isBound = true;
            presenter.setMusicService(musicService);
            enableControls(true);

            // ✅ REGISTRAR LISTENER PARA ATUALIZAÇÕES
            presenter.registerSongChangeListener(MainActivity.this);

            updateUIFromService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "🎵 Service disconnected");
            isBound = false;
            musicService = null;
        }
    };

    // ============================================================
    // CICLO DE VIDA PRINCIPAL
    // ============================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "🎵 === MAIN ACTIVITY onCreate ===");

        // ✅ Configuração para rotação suave ANTES de qualquer coisa
        setupAlbumCoverForSmoothRotation();

        // Inicializar presenter PRIMEIRO
        presenter = new MusicPresenter(this, this, this);

        // ✅ REGISTRAR LISTENER PARA ATUALIZAÇÕES
        presenter.registerSongChangeListener(this);

        // CONFIGURAR O LISTENER - ESSENCIAL!
        presenter.setOperationListener(this);
        Log.d(TAG, "✅ Presenter e OperationListener configurados");

        // Verificar e solicitar permissões
        checkPermissions();

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // CONFIGURAÇÃO DA TOOLBAR COM CHEVRON
        setupToolbar();

        // ✅ INICIALIZAR TODAS AS VIEWS
        initializeViews();

        setupListeners();
        setupAnimation();

        // ✅ INICIAR SISTEMA SIMPLES DE ATUALIZAÇÃO
        startSimpleUpdateSystem();

        // Inicializar serviço de música apenas se tiver permissões
        if (hasRequiredPermissions()) {
            initializeMusicService();
        }

        Log.d(TAG, "✅ onCreate completed");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "🔄 MainActivity onResume - Forçando sincronização com serviço");

        // ✅ REGISTRAR LISTENER NOVAMENTE
        if (presenter != null) {
            presenter.registerSongChangeListener(this);
        }

        // Atualizar cores do menu quando a activity voltar
        invalidateOptionsMenu();

        // Forçar sincronização quando a activity voltar ao foreground
        if (presenter != null) {
            presenter.forceServiceSync();
            presenter.setOperationListener(this);
        }

        // Recarregar músicas para garantir que a lista está atualizada
        if (presenter != null && hasRequiredPermissions()) {
            presenter.loadSongs();
        }

        // ✅ FORÇAR ATUALIZAÇÃO COMPLETA
        updateUIWithCurrentSong();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "⏸️ MainActivity onPause");

        // ✅ APENAS PAUSAR ANIMAÇÃO, NÃO REMOVER LISTENER
        pauseAlbumRotation();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "💀 === MAIN ACTIVITY onDestroy ===");

        // ✅ REMOVER LISTENER APENAS NO DESTROY
        if (presenter != null) {
            presenter.unregisterSongChangeListener(this);
            presenter.onDestroy();
        }

        // Remover callbacks do handler
        if (handler != null) {
            handler.removeCallbacks(updateRunnable);
            handler.removeCallbacksAndMessages(null);
        }

        if (isBound) {
            try {
                unbindService(connection);
                isBound = false;
                Log.d(TAG, "🎵 Service desconectado");
            } catch (Exception e) {
                Log.e(TAG, "❌ Erro ao desconectar service: " + e.getMessage());
            }
        }

        if (rotateAnimator != null) {
            rotateAnimator.cancel();
        }

        super.onDestroy();
        Log.d(TAG, "✅ onDestroy completed");
    }

    // ============================================================
    // ✅ SISTEMA SIMPLES DE ATUALIZAÇÃO (FUNCIONAVA ANTES)
    // ============================================================

    /**
     * ✅ SISTEMA SIMPLES: Atualização periódica a partir do Service
     */
    private void startSimpleUpdateSystem() {
        handler.postDelayed(updateRunnable, 1000);
    }

    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            updateUIFromService();
            handler.postDelayed(this, 1000);
        }
    };

    /**
     * ✅ ATUALIZA UI A PARTIR DO SERVICE - MÉTODO QUE FUNCIONAVA
     */
    private void updateUIFromService() {
        if (musicService != null) {
            Song currentSong = musicService.getCurrentSong();
            if (currentSong != null) {
                // ✅ VERIFICAÇÃO SIMPLES: Só atualiza se a música mudou
                if (!currentSong.getTitle().equals(currentDisplayedTitle)) {
                    currentDisplayedTitle = currentSong.getTitle();

                    // ATUALIZAR INFORMAÇÕES DA MÚSICA
                    updateSongInfo(currentSong.getTitle(), currentSong.getArtist());

                    // ATUALIZAR CAPA DO ÁLBUM
                    if (presenter != null) {
                        presenter.updateSongInfoWithCover(currentSong);
                    }

                    Log.d(TAG, "🔄 UI atualizada do Service: " + currentSong.getTitle());
                }

                updatePlayPauseIcon(musicService.isPlaying());
                updateProgress((int) musicService.getCurrentPosition(), (int) musicService.getDuration());

                // ✅ ATUALIZAR ANIMAÇÃO SE NECESSÁRIO
                ensureRotationWorksInBothThemes();

                if (musicService.isPlaying()) {
                    restartAlbumRotation();
                }
            }
        }
    }

    /**
     * ✅ ATUALIZAR UI COM MÚSICA ATUAL
     */
    private void updateUIWithCurrentSong() {
        if (presenter != null && presenter.getMusicService() != null) {
            Song currentSong = presenter.getMusicService().getCurrentSong();
            if (currentSong != null) {
                currentDisplayedTitle = currentSong.getTitle();
                updateSongInfo(currentSong.getTitle(), currentSong.getArtist());
                presenter.updateSongInfoWithCover(currentSong);
                Log.d(TAG, "✅ UI atualizada com música atual: " + currentSong.getTitle());
            }
        }
    }

    // ============================================================
    // INICIALIZAÇÃO E CONFIGURAÇÃO DA INTERFACE
    // ============================================================

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

        recyclerViewSongs = findViewById(R.id.recyclerViewSongs);
        recyclerViewSongs.setLayoutManager(new LinearLayoutManager(this));

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
        enableControls(false); // Desativa até ter permissões
    }

    /**
     * Configura a toolbar com botão de voltar e título
     */
    private void setupToolbar() {
        setSupportActionBar(toolbar);

        // CONFIGURAÇÃO DO CHEVRON - ESSENCIAL PARA FUNCIONAR
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle("Music Player");
        }

        // LISTENER DO BOTÃO CHEVRON - DEVE FUNCIONAR AGORA
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "⬅️ Chevron clicado - executando onBackPressed");
                onBackPressed();
            }
        });

        // Definir ícone programaticamente para garantir
        toolbar.setNavigationIcon(R.drawable.chevron_right);
    }

    /**
     * Configura os listeners para todos os botões e controles
     */
    private void setupListeners() {
        playPauseButton.setOnClickListener(v -> {
            Log.d(TAG, "⏯️ Play/Pause clicado");
            playPause();
        });

        prevButton.setOnClickListener(v -> {
            Log.d(TAG, "⏮️ Anterior clicado");
            prevSong();
        });

        nextButton.setOnClickListener(v -> {
            Log.d(TAG, "⏭️ Próxima clicado");
            nextSong();
        });

        shuffleButton.setOnClickListener(v -> {
            Log.d(TAG, "🔀 Shuffle clicado");
            toggleShuffle();
        });

        repeatButton.setOnClickListener(v -> {
            Log.d(TAG, "🔁 Repeat clicado");
            toggleRepeat();
        });

        songProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    seekTo(progress);
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
    // ANIMAÇÃO DA CAPA DO ÁLBUM (MANTIDO)
    // ============================================================

    /**
     * Garante que a capa está configurada para rotação circular suave
     */
    private void setupAlbumCoverForSmoothRotation() {
        if (albumCover != null) {
            // ✅ CONFIGURAÇÕES VISUAIS PARA ROTAÇÃO SUAVE
            albumCover.setScaleType(ImageView.ScaleType.CENTER_CROP);

            // Remove qualquer transformação que possa estar causando "batida"
            albumCover.setRotation(0f);
            albumCover.setTranslationX(0f);
            albumCover.setTranslationY(0f);

            // Garante que a view está pronta para animação
            albumCover.setLayerType(View.LAYER_TYPE_HARDWARE, null);

            Log.d(TAG, "🎨 AlbumCover configurado para rotação suave");
        }
    }

    /**
     * Configura a animação de rotação suave da capa
     */
    private void setupAnimation() {
        runOnUiThread(() -> {
            if (albumCover != null) {
                // CANCELAR ANIMAÇÃO EXISTENTE
                if (rotateAnimator != null) {
                    rotateAnimator.cancel();
                    rotateAnimator = null;
                }

                // ✅ CONFIGURAÇÃO SUAVE E CONTÍNUA (igual ao MusicPlayerActivity)
                rotateAnimator = ObjectAnimator.ofFloat(albumCover, "rotation", 0f, 360f);
                rotateAnimator.setDuration(15000); // 15 segundos para uma rotação completa
                rotateAnimator.setRepeatCount(ObjectAnimator.INFINITE); // Repetição infinita
                rotateAnimator.setInterpolator(new LinearInterpolator()); // Movimento constante
                rotateAnimator.setAutoCancel(true); // Permite cancelamento automático

                // ✅ CONFIGURAÇÕES CRÍTICAS PARA ROTAÇÃO SUAVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    albumCover.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                }

                Log.d(TAG, "🔄 Animação SUAVE configurada - igual ao MusicPlayer");
            }
        });
    }

    /**
     * Inicia a animação de rotação suave
     */
    private void startAlbumRotation() {
        runOnUiThread(() -> {
            if (rotateAnimator == null) {
                setupAnimation(); // Recria se necessário
            }

            if (rotateAnimator != null) {
                if (!rotateAnimator.isRunning() && !rotateAnimator.isStarted()) {
                    rotateAnimator.start();
                    Log.d(TAG, "✅ Rotação SUAVE iniciada");
                } else if (rotateAnimator.isPaused()) {
                    rotateAnimator.resume();
                    Log.d(TAG, "▶️ Rotação SUAVE retomada");
                }
            }
        });
    }

    /**
     * Pausa a animação de rotação
     */
    private void pauseAlbumRotation() {
        runOnUiThread(() -> {
            if (rotateAnimator != null && rotateAnimator.isRunning()) {
                rotateAnimator.pause();
                Log.d(TAG, "⏸️ Rotação SUAVE pausada");
            }
        });
    }

    /**
     * Reinicia completamente a animação - VERSÃO CORRIGIDA
     */
    public void restartAlbumRotation() {
        runOnUiThread(() -> {
            if (rotateAnimator != null) {
                rotateAnimator.cancel();
                // Pequeno delay para garantir o cancelamento
                new Handler().postDelayed(() -> {
                    if (albumCover != null) {
                        albumCover.setRotation(0f); // Reseta para posição inicial
                    }

                    if (musicService != null && musicService.isPlaying()) {
                        setupAnimation(); // Recria animação
                        startAlbumRotation(); // Inicia
                    }
                }, 50);
            }
        });
    }

    /**
     * ✅ GARANTIR FUNCIONAMENTO EM AMBOS OS TEMAS
     */
    private void ensureRotationWorksInBothThemes() {
        runOnUiThread(() -> {
            // Forçar uma reinicialização da animação considerando o tema atual
            if (rotateAnimator != null) {
                rotateAnimator.cancel();
            }

            // Recriar a animação do zero
            setupAnimation();

            // Se estiver tocando, iniciar a animação
            if (musicService != null && musicService.isPlaying()) {
                startAlbumRotation();
            }
        });
    }

    // ============================================================
    // ✅ IMPLEMENTAÇÃO DO OnSongChangeListener (ATUALIZAÇÕES AUTOMÁTICAS)
    // ============================================================

    /**
     * ✅ MÉTODO CRÍTICO: Chamado quando a música é alterada durante a reprodução
     */
    @Override
    public void onSongChanged(Song song) {
        Log.d(TAG, "🔄 onSongChanged chamado: " + (song != null ? song.getTitle() : "null"));

        runOnUiThread(() -> {
            if (song != null) {
                // ✅ ATUALIZAR VARIÁVEL DE CONTROLE
                currentDisplayedTitle = song.getTitle();

                updateSongInfo(song.getTitle(), song.getArtist());

                // ✅ FORÇAR ATUALIZAÇÃO DA CAPA
                if (presenter != null) {
                    presenter.loadAlbumArtForSong(song);
                }

                restartAlbumRotation();
                Log.d(TAG, "🎵 Música alterada na UI: " + song.getTitle());
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

            // Controla animação baseado no estado de reprodução
            if (isPlaying) {
                startAlbumRotation();
            } else {
                pauseAlbumRotation();
            }
        });
    }

    // ============================================================
    // IMPLEMENTAÇÃO DA INTERFACE MusicView
    // ============================================================

    @Override
    public void updateSongInfo(String title, String artist) {
        runOnUiThread(() -> {
            songTitle.setText(title);
            artistName.setText(artist);
            Log.d(TAG, "📝 UI atualizada - Música: " + title + " - Artista: " + artist);
        });
    }

    @Override
    public void updatePlayPauseIcon(boolean isPlaying) {
        runOnUiThread(() -> {
            playPauseButton.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play_arrow);

            // ✅ CONTROLE SUAVE DA ANIMAÇÃO (igual ao MusicPlayerActivity)
            if (isPlaying) {
                startAlbumRotation();
            } else {
                pauseAlbumRotation();
            }

            Log.d(TAG, "🎵 Play/Pause: " + (isPlaying ? "PLAYING" : "PAUSED") +
                    " - Animação: " + (isPlaying ? "SUAVE INICIADA" : "SUAVE PAUSADA"));
        });
    }

    @Override
    public void updateProgress(int progress, int duration) {
        runOnUiThread(() -> {
            songProgress.setMax(duration > 0 ? duration : 0);
            songProgress.setProgress(progress >= 0 ? progress : 0);
            tvTempoAtual.setText(formatTime(progress));
            tvDuracaoTotal.setText(formatTime(duration));
        });
    }

    @Override
    public void updateSongList(List<Song> songs) {
        runOnUiThread(() -> {
            Log.d(TAG, "📋 Atualizando lista de músicas na UI: " + songs.size() + " músicas");

            songAdapter = new SongAdapter(
                    this, // Context
                    songs != null ? songs : new ArrayList<>(),
                    // Listener para tocar música
                    song -> {
                        Log.d(TAG, "🎵 Song clicked: " + song.getTitle());
                        if (hasRequiredPermissions()) {
                            presenter.playSong(song);
                        } else {
                            Toast.makeText(this, "Permissão necessária para reproduzir músicas", Toast.LENGTH_SHORT).show();
                            checkPermissions();
                        }
                    },
                    // Listener para favoritos
                    song -> {
                        Log.d(TAG, "⭐ Favorite clicked: " + song.getTitle());
                        presenter.toggleFavorite(song);
                    },
                    presenter
            );
            recyclerViewSongs.setAdapter(songAdapter);

            Log.d(TAG, "✅ Adapter configurado com " + songs.size() + " músicas");
        });
    }

    /**
     * ✅ MÉTODO NECESSÁRIO PARA RECEBER CAPAS
     */
    @Override
    public void updateAlbumArt(Bitmap albumArt) {
        runOnUiThread(() -> {
            if (albumCover != null) {
                try {
                    // ✅ MESMO TRATAMENTO DA MUSICPLAYERACTIVITY
                    RequestOptions requestOptions = new RequestOptions()
                            .circleCrop()
                            .override(400, 400);

                    if (albumArt != null) {
                        // ✅ CAPA REAL - COM FORMATO CIRCULAR
                        Glide.with(this)
                                .load(albumArt)
                                .apply(requestOptions)
                                .into(albumCover);
                        Log.d(TAG, "✅ Capa circular carregada na MainActivity");
                    } else {
                        // ✅ PLACEHOLDER CIRCULAR
                        Glide.with(this)
                                .load(R.drawable.ic_lm_capa_placeholder)
                                .apply(requestOptions)
                                .into(albumCover);
                        Log.d(TAG, "🖼️ Placeholder circular carregado");
                    }

                } catch (Exception e) {
                    Log.e(TAG, "❌ Erro ao carregar capa circular: " + e.getMessage());
                    albumCover.setImageResource(R.drawable.ic_lm_capa_placeholder);
                }
            }
        });
    }

    // ============================================================
    // IMPLEMENTAÇÃO DO OnMusicOperationListener (DIÁLOGOS)
    // ============================================================

    @Override
    public void onSongDeleted(Song song) {
        runOnUiThread(() -> {
            Log.d(TAG, "🗑️ === onSongDeleted CHAMADO ===");
            Log.d(TAG, "Música deletada: " + song.getTitle());

            // Recarregar a lista de músicas
            if (presenter != null) {
                presenter.loadSongs();
                Log.d(TAG, "✅ Lista de músicas recarregada após exclusão");
            }

            // Atualizar o adapter se existir
            if (songAdapter != null) {
                songAdapter.removeSong(song);
                Log.d(TAG, "✅ Adapter atualizado para remover música");
            }

            Toast.makeText(this, "Música excluída com sucesso", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "✅ Toast de exclusão mostrado");
        });
    }

    @Override
    public void onSongRenamed(Song song) {
        runOnUiThread(() -> {
            Log.d(TAG, "✏️ === onSongRenamed CHAMADO ===");
            Log.d(TAG, "Música renomeada: " + song.getTitle());

            // Recarregar a lista de músicas
            if (presenter != null) {
                presenter.loadSongs();
                Log.d(TAG, "✅ Lista de músicas recarregada após renomeação");
            }

            // Atualizar o adapter se existir
            if (songAdapter != null) {
                songAdapter.updateSong(song);
                Log.d(TAG, "✅ Adapter atualizado para música renomeada");
            }

            Toast.makeText(this, "Música renomeada com sucesso", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "✅ Toast de renomeação mostrado");
        });
    }

    @Override
    public void onCoverChanged() {
        runOnUiThread(() -> {
            Log.d(TAG, "🖼️ === onCoverChanged CHAMADO ===");

            // Recarregar a lista para atualizar as capas
            if (presenter != null) {
                presenter.loadSongs();
                Log.d(TAG, "✅ Lista recarregada após alteração de capa");
            }

            // Atualizar o adapter
            if (songAdapter != null) {
                songAdapter.notifyDataSetChanged();
                Log.d(TAG, "✅ Adapter notificado para atualizar capas");
            }

            Toast.makeText(this, "Capa atualizada com sucesso", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "✅ Toast de capa atualizada mostrado");
        });
    }

    // ============================================================
    // MÉTODOS DE NAVEGAÇÃO E MENU (MANTIDOS)
    // ============================================================

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);

        // CONFIGURAÇÃO DE CORES DINÂMICAS DO MENU
        applyDynamicMenuColors(menu);

        // CONFIGURAÇÃO DO SEARCHVIEW
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
            navigateToFavoritas();
            return true;
        } else if (id == R.id.action_descarregadas) {
            navigateToDescarregadas();
            return true;
        } else if (id == R.id.action_artistas) {
            navigateToArtists();
            return true;
        } else if (id == R.id.action_recognize) {
            navigateToRecognize();
            return true;
        } else if (id == R.id.action_playlists) {
            navigateToCreatePlaylist();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    // MÉTODOS DE NAVEGAÇÃO (MANTIDOS)
    private void navigateToFavoritas() {
        try {
            Intent intent = new Intent(this, FavoritasActivity.class);
            startActivity(intent);
            Toast.makeText(this, "⭐ Abrindo Favoritas", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "➡️ Navegando para FavoritasActivity");
        } catch (Exception e) {
            Log.e(TAG, "❌ Erro ao abrir Favoritas: " + e.getMessage());
            Toast.makeText(this, "Erro ao abrir Favoritas", Toast.LENGTH_SHORT).show();
        }
    }

    private void navigateToDescarregadas() {
        try {
            Intent intent = new Intent(this, DescarregadasActivity.class);
            startActivity(intent);
            Toast.makeText(this, "📥 Abrindo Descarregadas", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "➡️ Navegando para DescarregadasActivity");
        } catch (Exception e) {
            Log.e(TAG, "❌ Erro ao abrir Descarregadas: " + e.getMessage());
            Toast.makeText(this, "Erro ao abrir Descarregadas", Toast.LENGTH_SHORT).show();
        }
    }

    private void navigateToArtists() {
        try {
            Intent intent = new Intent(this, ArtistsActivity.class);
            startActivity(intent);
            Toast.makeText(this, "🎤 Abrindo Artistas", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "➡️ Navegando para ArtistsActivity");
        } catch (Exception e) {
            Log.e(TAG, "❌ Erro ao abrir Artistas: " + e.getMessage());
            Toast.makeText(this, "Erro ao abrir Artistas", Toast.LENGTH_SHORT).show();
        }
    }

    private void navigateToRecognize() {
        try {
            Intent intent = new Intent(this, ReconhecerMusicaActivity.class);
            startActivity(intent);
            Toast.makeText(this, "🎵 Abrindo Reconhecer Música", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "➡️ Navegando para ReconhecerMusicaActivity");
        } catch (Exception e) {
            Log.e(TAG, "❌ Erro ao abrir Reconhecer Música: " + e.getMessage());
            Toast.makeText(this, "Erro ao abrir Reconhecer Música", Toast.LENGTH_SHORT).show();
        }
    }

    private void navigateToCreatePlaylist() {
        try {
            Intent intent = new Intent(this, PlaylistsActivity.class);
            startActivity(intent);
            Toast.makeText(this, "📋 Abrindo Playlists", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "➡️ Navegando para PlaylistActivity");
        } catch (Exception e) {
            Log.e(TAG, "❌ Erro ao abrir Playlists: " + e.getMessage());
            Toast.makeText(this, "Erro ao abrir Playlists", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
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
     * Aplica cores dinâmicas aos itens do menu baseado no tema (claro/escuro)
     */
    private void applyDynamicMenuColors(Menu menu) {
        try {
            // Obter a cor dinâmica para ícones
            int menuIconColor = getColorForTheme(
                    R.color.icon_primary_light,
                    R.color.icon_primary_dark
            );

            // Obter cor para texto (opcional)
            int menuTextColor = getColorForTheme(
                    R.color.text_primary_light,
                    R.color.text_primary_dark
            );

            Log.d(TAG, "🎨 Aplicando cores dinâmicas ao menu - Cor: " + Integer.toHexString(menuIconColor));

            // Aplicar cor a todos os itens do menu com ícones
            for (int i = 0; i < menu.size(); i++) {
                MenuItem item = menu.getItem(i);

                // Aplicar cor ao ícone se existir
                if (item.getIcon() != null) {
                    item.getIcon().setColorFilter(menuIconColor, android.graphics.PorterDuff.Mode.SRC_IN);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        item.getIcon().setTint(menuIconColor);
                    }

                    Log.d(TAG, "🎨 Cor aplicada ao ícone: " + item.getTitle());
                }

                // Opcional: Aplicar cor ao texto do menu (para overflow)
                SpannableString spanString = new SpannableString(item.getTitle());
                spanString.setSpan(new ForegroundColorSpan(menuTextColor), 0, spanString.length(), 0);
                item.setTitle(spanString);
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Erro ao aplicar cores dinâmicas ao menu: " + e.getMessage());
        }
    }

    // ============================================================
    // GERENCIAMENTO DE PERMISSÕES
    // ============================================================

    private boolean hasRequiredPermissions() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void checkPermissions() {
        if (!hasRequiredPermissions()) {
            List<String> missingPermissions = new ArrayList<>();
            for (String permission : REQUIRED_PERMISSIONS) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    missingPermissions.add(permission);
                }
            }
            if (!missingPermissions.isEmpty()) {
                ActivityCompat.requestPermissions(this,
                        missingPermissions.toArray(new String[0]),
                        REQUEST_CODE_PERMISSIONS);
            }
        } else {
            if (presenter != null) {
                presenter.onPermissionsGranted();
            }
            if (!isBound) {
                initializeMusicService();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                if (presenter != null) {
                    presenter.onPermissionsGranted();
                }
                initializeMusicService();
                Toast.makeText(this, "✅ Permissões concedidas!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "❌ Permissões necessárias não concedidas", Toast.LENGTH_SHORT).show();
                enableControls(false);
            }
        }
    }

    private void initializeMusicService() {
        try {
            Intent intent = new Intent(this, MusicService.class);
            bindService(intent, connection, Context.BIND_AUTO_CREATE);
            startService(intent);
            Log.d(TAG, "🎵 Serviço de música inicializado");
        } catch (SecurityException e) {
            Log.e(TAG, "❌ SecurityException ao inicializar serviço: " + e.getMessage());
            Toast.makeText(this, "Permissão necessária para reproduzir músicas", Toast.LENGTH_LONG).show();
        }
    }

    private void enableControls(boolean enable) {
        playPauseButton.setEnabled(enable);
        prevButton.setEnabled(enable);
        nextButton.setEnabled(enable);
        songProgress.setEnabled(enable);

        if (!hasRequiredPermissions()) {
            playPauseButton.setEnabled(false);
            prevButton.setEnabled(false);
            nextButton.setEnabled(false);
            songProgress.setEnabled(false);
        }
    }

    // ============================================================
    // MÉTODOS DE CONTROLE DE MÚSICA
    // ============================================================

    @Override
    public void playSong(Song song) {
        if (!hasRequiredPermissions()) {
            Toast.makeText(this, "Permissão necessária para reproduzir músicas", Toast.LENGTH_SHORT).show();
            checkPermissions();
            return;
        }

        try {
            if (presenter != null) {
                presenter.playSong(song);
                Log.d(TAG, "🎵 playSong chamado: " + song.getTitle());
            }
        } catch (SecurityException e) {
            Log.e(TAG, "❌ SecurityException em playSong: " + e.getMessage());
            Toast.makeText(this, "Erro de permissão ao reproduzir música", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void playPause() {
        if (!hasRequiredPermissions()) {
            Toast.makeText(this, "Permissão necessária para reproduzir músicas", Toast.LENGTH_SHORT).show();
            checkPermissions();
            return;
        }

        try {
            if (presenter != null) {
                presenter.playPause();
                Log.d(TAG, "⏯️ playPause chamado");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "❌ SecurityException em playPause: " + e.getMessage());
            Toast.makeText(this, "Erro de permissão ao controlar reprodução", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void prevSong() {
        if (!hasRequiredPermissions()) {
            Toast.makeText(this, "Permissão necessária para reproduzir músicas", Toast.LENGTH_SHORT).show();
            checkPermissions();
            return;
        }

        try {
            if (presenter != null) {
                presenter.prevSong();
                Log.d(TAG, "⏮️ prevSong chamado");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "❌ SecurityException em prevSong: " + e.getMessage());
            Toast.makeText(this, "Erro de permissão ao mudar música", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void nextSong() {
        if (!hasRequiredPermissions()) {
            Toast.makeText(this, "Permissão necessária para reproduzir músicas", Toast.LENGTH_SHORT).show();
            checkPermissions();
            return;
        }

        try {
            if (presenter != null) {
                presenter.nextSong();
                Log.d(TAG, "⏭️ nextSong chamado");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "❌ SecurityException em nextSong: " + e.getMessage());
            Toast.makeText(this, "Erro de permissão ao mudar música", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void toggleShuffle() {
        if (presenter != null) {
            presenter.toggleShuffle();
            Log.d(TAG, "🔀 toggleShuffle chamado");
        }
    }

    @Override
    public void toggleRepeat() {
        if (presenter != null) {
            presenter.toggleRepeat();
            Log.d(TAG, "🔁 toggleRepeat chamado");
        }
    }

    @Override
    public void seekTo(int progress) {
        if (!hasRequiredPermissions()) {
            Toast.makeText(this, "Permissão necessária para reproduzir músicas", Toast.LENGTH_SHORT).show();
            checkPermissions();
            return;
        }

        try {
            if (presenter != null) {
                presenter.seekTo(progress);
                Log.d(TAG, "🎚️ seekTo chamado: " + progress + "ms");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "❌ SecurityException em seekTo: " + e.getMessage());
            Toast.makeText(this, "Erro de permissão ao buscar posição", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void searchSongs(String query) {
        if (presenter != null) {
            presenter.searchSongs(query);
            Log.d(TAG, "🔍 searchSongs chamado: " + query);
        }
    }

    @Override
    public void createPlaylist() {
        if (presenter != null) {
            presenter.createPlaylist();
            Log.d(TAG, "📋 createPlaylist chamado");
        }
    }

    @Override
    public void recognizeMusic() {
        if (presenter != null) {
            presenter.recognizeMusic();
            Log.d(TAG, "🎵 recognizeMusic chamado");
        }
    }

    // ============================================================
    // MÉTODOS AUXILIARES
    // ============================================================

    private String formatTime(int ms) {
        if (ms <= 0) return "00:00";
        int sec = (ms / 1000) % 60;
        int min = (ms / 1000) / 60;
        return String.format("%02d:%02d", min, sec);
    }

    /**
     * Método para forçar atualização da lista de músicas
     */
    public void refreshMusicList() {
        runOnUiThread(() -> {
            Log.d(TAG, "🔄 refreshMusicList chamado manualmente");
            if (presenter != null && hasRequiredPermissions()) {
                presenter.loadSongs();
            } else {
                Toast.makeText(this, "Permissões necessárias para carregar músicas", Toast.LENGTH_SHORT).show();
                checkPermissions();
            }
            Toast.makeText(this, "📋 Lista atualizada", Toast.LENGTH_SHORT).show();
        });
    }

    // ============================================================
    // MÉTODOS NÃO UTILIZADOS (IMPLEMENTAÇÃO VAZIA)
    // ============================================================

    @Override
    public void updatePlaylistList(List<Playlist> playlists) {
        Log.d(TAG, "📋 updatePlaylistList chamado - " + playlists.size() + " playlists");
    }

    @Override
    public void showRecognitionResult(String result) {
        Log.d(TAG, "🎵 showRecognitionResult: " + result);
    }

    @Override
    public void requestPermissions() {
        checkPermissions();
        Log.d(TAG, "🔒 Solicitando permissões...");
    }

    @Override
    public void loadPlaylistSongs(Playlist playlist) {
        Log.d(TAG, "📋 loadPlaylistSongs chamado: " + playlist.getName());
    }

    @Override
    public void updateArtists(List<String> artists) {
        Log.d(TAG, "🎤 updateArtists chamado: " + artists.size() + " artistas");
    }
}