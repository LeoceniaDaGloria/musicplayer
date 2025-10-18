package com.example.musicplayer.presenter;

import android.Manifest;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.musicplayer.db.MusicRepository;
import com.example.musicplayer.model.Playlist;
import com.example.musicplayer.model.Song;
import com.example.musicplayer.services.MusicService;
import com.example.musicplayer.utils.FileUtils;
import com.example.musicplayer.view.ArtistsActivity;
import com.example.musicplayer.view.DescarregadasActivity;
import com.example.musicplayer.view.FavoritasActivity;
import com.example.musicplayer.view.MainActivity;
import com.example.musicplayer.view.MusicPlayerActivity;
import com.example.musicplayer.view.MusicView;
import com.example.musicplayer.view.PlaylistsActivity;
import com.google.android.exoplayer2.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class MusicPresenter {
    private static final String TAG = "MusicPresenter";
    private final MusicView view;
    private final MusicRepository repository;
    private MusicService musicService;
    private boolean isBound = false;
    private final Context context;
    private final LifecycleOwner lifecycleOwner;
    private List<Song> allSongs = new ArrayList<>();
    private int currentSongIndex = -1;
    private boolean isShuffleMode = false;
    private boolean isRepeatMode = false;
    private final Handler handler = new Handler();

    private boolean isHandlingCompletion = false;

    //  Variável única para bloquear toda navegação
    private boolean isHandlingNavigation = false;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    // ✅ CORREÇÃO: Listener para operações (diálogos, etc.)
    private OnMusicOperationListener operationListener;

    // ✅ CORREÇÃO: Lista de listeners para múltiplas activities
    private List<OnSongChangeListener> songChangeListeners = new ArrayList<>();

    private final Runnable updateProgressAction = new Runnable() {
        @Override
        public void run() {
            if (musicService != null && musicService.isPlaying()) {
                view.updateProgress((int) musicService.getCurrentPosition(), (int) musicService.getDuration());
            }
            handler.postDelayed(this, 1000);
        }
    };

    // Service Connection
        private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "Service conectado via Binding");
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicService = binder.getService();
            isBound = true;
            musicService.setPresenter(MusicPresenter.this);

            // ✅ RESETAR controles
            isHandlingCompletion = false;
            isHandlingNavigation = false;

            addPlayerListener();
            updatePlayPauseState();

            if (musicService.getCurrentSong() != null) {
                // ✅ CORREÇÃO: SINCRONIZAR com estado REAL do Service
                syncWithServiceState();

                Song currentSong = musicService.getCurrentSong();
                updateSongInfoWithCover(currentSong);
                view.updatePlayPauseIcon(musicService.isPlaying());

                Log.d(TAG, "✅ Service conectado - Estado sincronizado: " + currentSong.getTitle());
            }

            Log.d(TAG, "Service conectado - controles resetados e sincronizados");
        }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.w(TAG, "Service desconectado");
                isBound = false;
                musicService = null;
                // ✅ RESETAR variável de controle
                isHandlingCompletion = false;
                isHandlingNavigation = false;
            }
    };

    public MusicPresenter(MusicView view, Context context, LifecycleOwner lifecycleOwner) {
        this.view = view;
        this.context = context;
        this.lifecycleOwner = lifecycleOwner;
        this.repository = new MusicRepository(context);

        observeSongs();
        observePlaylists();
        startProgressUpdate();
        loadSongsIfNeeded();
        bindMusicService();

        Log.d(TAG, "MusicPresenter inicializado com sucesso");
    }

    // ============================================================
    // SISTEMA DE LISTENERS PARA MÚLTIPLAS ACTIVITIES
    // ============================================================

    /**
     *  INTERFACE para listeners de mudança de música
     */
    public interface OnSongChangeListener {
        void onSongChanged(Song song);
        void onPlaybackStateChanged(boolean isPlaying);
    }

    /**
     *  REGISTRAR listener para receber atualizações de música
     */
    public void registerSongChangeListener(OnSongChangeListener listener) {
        if (!songChangeListeners.contains(listener)) {
            songChangeListeners.add(listener);
            Log.d(TAG, "🎵 Listener registrado: " + listener.getClass().getSimpleName() +
                    " | Total: " + songChangeListeners.size());
        }
    }

    /**
     * ✅ REMOVER listener (evitar vazamentos de memória)
     */
    public void unregisterSongChangeListener(OnSongChangeListener listener) {
        songChangeListeners.remove(listener);
        Log.d(TAG, "🎵 Listener removido: " + listener.getClass().getSimpleName() +
                " | Restantes: " + songChangeListeners.size());
    }

    /**
     * ✅ NOTIFICAR TODOS OS LISTENERS sobre mudança de música
     */
    private void notifyAllSongChangeListeners(Song song) {
        for (OnSongChangeListener listener : new ArrayList<>(songChangeListeners)) {
            try {
                listener.onSongChanged(song);
                Log.d(TAG, "✅ Listener notificado: " + listener.getClass().getSimpleName());
            } catch (Exception e) {
                Log.e(TAG, "❌ Erro ao notificar listener: " + e.getMessage());
            }
        }
    }

    /**
     * NOTIFICAR TODOS OS LISTENERS sobre mudança de estado
     */
    private void notifyAllPlaybackStateListeners(boolean isPlaying) {
        for (OnSongChangeListener listener : new ArrayList<>(songChangeListeners)) {
            try {
                listener.onPlaybackStateChanged(isPlaying);
            } catch (Exception e) {
                Log.e(TAG, "Erro ao notificar estado de reprodução: " + e.getMessage());
            }
        }
    }

    /**
     *  FORÇAR SINCRONIZAÇÃO COMPLETA EM TODAS AS ACTIVITIES
     */
    public void forceFullSync() {
        if (musicService != null && musicService.getCurrentSong() != null) {
            Song currentSong = musicService.getCurrentSong();
            boolean isPlaying = musicService.isPlaying();

            onSongChanged(currentSong);
            onPlaybackStateChanged(isPlaying);

            Log.d(TAG, "🔄 Sincronização forçada - Música: " + currentSong.getTitle() +
                    " | Tocando: " + isPlaying + " | Listeners: " + songChangeListeners.size());
        }
    }

    // ============================================================
    // CONTROLE INTELIGENTE DE FOREGROUND/BACKGROUND
    // ============================================================

    /**
     * Chamado quando o app vai para background
     */
    public void onAppBackgrounded() {
        Log.d(TAG, "App indo para background");

        if (musicService != null && musicService.isPlaying()) {
            musicService.promoteToForeground();
            Log.d(TAG, "Promovendo serviço para foreground (app em background)");
        }
    }

    /**
     * Chamado quando o app volta para foreground
     */
    public void onAppForegrounded() {
        Log.d(TAG, "App voltando para foreground");

        if (musicService != null && musicService.isInForeground()) {
            musicService.demoteToBackground();
            Log.d(TAG, "Rebaixando serviço para background (app em foreground)");
        }
    }

    /**
     * Chamado quando o usuário está saindo do app
     */
    public void onUserLeavingApp() {
        Log.d(TAG, "Usuário saindo do app");
        onAppBackgrounded();
    }

    // ============================================================
    // MÉTODOS PARA VERIFICAÇÃO DE PERMISSÕES
    // ============================================================

    /**
     * Verifica se tem permissão para modificar arquivos
     */
    private boolean hasFileWritePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ - precisa de MANAGE_EXTERNAL_STORAGE
            return Environment.isExternalStorageManager();
        } else {
            // Android 10 e abaixo - WRITE_EXTERNAL_STORAGE
            return ContextCompat.checkSelfPermission(context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    /**
     * Solicita permissão para gerenciar arquivos (Android 11+)
     */
    public void requestFileWritePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                Uri uri = Uri.fromParts("package", context.getPackageName(), null);
                intent.setData(uri);

                if (context instanceof android.app.Activity) {
                    ((android.app.Activity) context).startActivityForResult(intent, 1001);
                } else {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                }

                Log.d(TAG, "Solicitando permissão MANAGE_EXTERNAL_STORAGE");
            } catch (Exception e) {
                Log.e(TAG, "Erro ao solicitar permissão: " + e.getMessage());

                // Fallback para intent mais simples
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                context.startActivity(intent);
            }
        } else {
            // Para Android 10 e abaixo, solicitar WRITE_EXTERNAL_STORAGE
            if (context instanceof android.app.Activity) {
                ActivityCompat.requestPermissions((android.app.Activity) context,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1002);
            }
        }
    }

    /**
     * Verifica e solicita permissão se necessário
     */
    private boolean checkAndRequestFilePermission() {
        if (!hasFileWritePermission()) {
            Log.w(TAG, "Sem permissão para modificar arquivos - solicitando...");

            if (context instanceof android.app.Activity) {
                ((android.app.Activity) context).runOnUiThread(() -> {
                    showPermissionDialog();
                });
            }
            return false;
        }
        return true;
    }

    /**
     * Mostra diálogo explicativo sobre permissão
     */
    private void showPermissionDialog() {
        if (context instanceof android.app.Activity) {
            android.app.Activity activity = (android.app.Activity) context;
            activity.runOnUiThread(() -> {
                new AlertDialog.Builder(activity)
                        .setTitle("Permissão Necessária")
                        .setMessage("Para renomear ou excluir músicas, é necessário conceder permissão de gerenciamento de arquivos.")
                        .setPositiveButton("Conceder", (dialog, which) -> {
                            requestFileWritePermission();
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
            });
        }
    }

    /**
     * Verifica permissão específica para acessar galeria (Android 14+)
     */
    public boolean hasGalleryPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context,
                    Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return ContextCompat.checkSelfPermission(context,
                    Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    /**
     * Solicita permissão para galeria
     */
    public void requestGalleryPermission() {
        List<String> permissionsToRequest = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasGalleryPermission()) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                requestFileWritePermission();
                return;
            }
        } else {
            if (ContextCompat.checkSelfPermission(context,
                    Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }

        if (!permissionsToRequest.isEmpty() && context instanceof android.app.Activity) {
            ActivityCompat.requestPermissions((android.app.Activity) context,
                    permissionsToRequest.toArray(new String[0]), 1003);
        }
    }

    /**
     * Mostra diálogo explicativo sobre permissão de galeria
     */
    private void showGalleryPermissionDialog() {
        if (context instanceof android.app.Activity) {
            android.app.Activity activity = (android.app.Activity) context;
            activity.runOnUiThread(() -> {
                new AlertDialog.Builder(activity)
                        .setTitle("Permissão para Acessar Fotos")
                        .setMessage("Para escolher uma imagem da galeria, é necessário conceder permissão para acessar suas fotos.")
                        .setPositiveButton("Conceder", (dialog, which) -> {
                            requestGalleryPermission();
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
            });
        }
    }

    // ============================================================
    // COMUNICAÇÃO COM O SERVICE COM A TRANSIÇÃO AUTOMÁTICA
    // ============================================================

    public void bindMusicService() {
        try {
            Intent intent = new Intent(context, MusicService.class);
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
            Log.d(TAG, "Tentando binding com MusicService");
        } catch (Exception e) {
            Log.e(TAG, "Erro no binding com MusicService: " + e.getMessage());
        }
    }

    /**
     *  METODO: Sincroniza índice atual com o serviço
     */
    private void syncCurrentIndexWithService() {
        if (musicService != null && musicService.getCurrentSong() != null) {
            Song currentServiceSong = musicService.getCurrentSong();
            int serviceIndex = findSongIndex(currentServiceSong);

            if (serviceIndex != currentSongIndex) {
                Log.d(TAG, "🔄 Sincronizando índice: " + currentSongIndex + " → " + serviceIndex);
                currentSongIndex = serviceIndex;
            }
        }
    }

    public void playSong(Song song) {
        syncCurrentIndexWithService();
        if (song == null) {
            Log.e(TAG, "Song é nulo. Não é possível reproduzir.");
            return;
        }

        Log.d(TAG, "Solicitando reprodução: " + song.getTitle() + " - " + song.getArtist());

        //  Extrair metadados antes de reproduzir
        extractAndUpdateMusicMetadata(song);

        if (musicService != null && isBound) {
            // Usar o novo método que configura a playlist completa
            setPlaylistForAutomaticTransition(allSongs, findSongIndex(song));
            musicService.playSong(song);

            updateSongInfoWithCover(song);
            view.updatePlayPauseIcon(true);

            Log.d(TAG, "Música reproduzida via binding: " + song.getTitle() + " - " + song.getArtist());
        } else {
            // ... resto do código existente
        }
    }
    public void playPause() {
        if (musicService != null && isBound) {
            musicService.playPause();
            view.updatePlayPauseIcon(musicService.isPlaying());
            Log.d(TAG, "Play/Pause via binding. Tocando: " + musicService.isPlaying());
        } else {
            Log.w(TAG, "Service não bound, usando intent para Play/Pause");
            Intent serviceIntent = new Intent(context, MusicService.class);
            serviceIntent.setAction("ACTION_PLAY_PAUSE");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }

            bindMusicService();
        }
    }

    public void nextSong() {
        Log.d(TAG, "🎵 SOLICITANDO PRÓXIMA MÚSICA");

        // ✅ SINCRONIZAR PRIMEIRO
        syncWithServiceState();

        if (isHandlingNavigation) {
            Log.w(TAG, "🚨 Navegação já em andamento - ignorando");
            return;
        }

        isHandlingNavigation = true;

        try {
            if (musicService != null && isBound) {
                musicService.nextSong();
                Log.d(TAG, "✅ Próxima música delegada ao service");

                // ✅ SINCRONIZAR após navegação
                new Handler().postDelayed(() -> {
                    syncWithServiceState();
                    isHandlingNavigation = false;
                }, 800);
            } else {
                // ... fallback code
                isHandlingNavigation = false;
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Erro em nextSong: " + e.getMessage());
            isHandlingNavigation = false;
        }
    }

    public void prevSong() {
        Log.d(TAG, "🎵 SOLICITANDO MÚSICA ANTERIOR (MANUAL)");

        syncCurrentIndexWithService();

        if (isHandlingNavigation) {
            Log.w(TAG, "🚨 BLOQUEADO: Navegação já em andamento - ignorando prevSong");
            return;
        }

        isHandlingNavigation = true;

        try {
            if (musicService != null && isBound) {
                musicService.prevSong();
                Log.d(TAG, "✅ Música anterior delegada ao service");
            } else if (allSongs != null && !allSongs.isEmpty()) {
                Log.w(TAG, "Service não bound, usando lógica local + intent");

                int prevIndex;
                if (currentSongIndex > 0) {
                    prevIndex = currentSongIndex - 1;
                } else {
                    prevIndex = allSongs.size() - 1;
                }

                Log.d(TAG, "🔍 Índice anterior calculado: " + prevIndex);

                if (prevIndex >= 0 && prevIndex < allSongs.size()) {
                    Song prevSong = allSongs.get(prevIndex);

                    Log.d(TAG, "🎵 Iniciando música anterior: " + prevSong.getTitle());

                    // ✅ NÃO ATUALIZAR currentSongIndex AQUI
                    Intent intent = new Intent(context, MusicService.class);
                    intent.setAction("PLAY");
                    intent.putExtra("path", prevSong.getPath());
                    intent.putExtra("title", prevSong.getTitle());
                    intent.putExtra("artist", prevSong.getArtist());

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent);
                    } else {
                        context.startService(intent);
                    }

                    updateSongInfoWithCover(prevSong);
                }

                bindMusicService();
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Erro em prevSong: " + e.getMessage());
        } finally {
            new Handler().postDelayed(() -> {
                isHandlingNavigation = false;
                Log.d(TAG, "🔓 Bloqueio de navegação liberado");
            }, 1000);
        }
    }

    public void seekTo(int progress) {
        if (musicService != null && isBound) {
            musicService.seekTo(progress);
            view.updateProgress(progress, (int) musicService.getDuration());
            Log.d(TAG, "Seek para: " + progress + "ms via binding");
        } else {
            Log.w(TAG, "Service não bound para seek");
        }
    }

    // ============================================================
    // MÉTODOS AUXILIARES COM TRANSIÇÃO AUTOMÁTICA
    // ============================================================

    private int findSongIndex(Song song) {
        if (allSongs == null || song == null) return -1;

        Log.d(TAG, "🔍 Buscando índice para: " + song.getTitle() + " - " + song.getArtist());

        for (int i = 0; i < allSongs.size(); i++) {
            Song s = allSongs.get(i);
            // ✅ CORREÇÃO: Verificar por ID ou combinação título/artista
            if (s.getId() == song.getId() ||
                    (s.getTitle().equals(song.getTitle()) && s.getArtist().equals(song.getArtist()))) {
                Log.d(TAG, "✅ Índice encontrado: " + i);
                return i;
            }
        }

        Log.w(TAG, "⚠️ Índice não encontrado para: " + song.getTitle() + " - " + song.getArtist());
        return -1;
    }

    /**
     * ✅ CORREÇÃO: Listener do Player com notificação para todos os listeners
     */
    private void addPlayerListener() {
        if (musicService != null && musicService.getPlayer() != null) {
            musicService.getPlayer().addListener(new Player.Listener() {
                @Override
                public void onMediaItemTransition(com.google.android.exoplayer2.MediaItem mediaItem, int reason) {
                    Log.d(TAG, "🎵 Transição de mídia - Razão: " + reason);

                    // ✅ DEIXAR TODAS as transições passarem
                    if (musicService.getCurrentSong() != null) {
                        Song currentSong = musicService.getCurrentSong();

                        // ✅ CORREÇÃO CRÍTICA: Buscar música REAL do Service
                        Song realCurrentSong = musicService.getCurrentSong();
                        if (realCurrentSong != null) {
                            // ✅ ATUALIZAR ÍNDICE baseado na música REAL do Service
                            currentSongIndex = findSongIndex(realCurrentSong);
                            currentSong = realCurrentSong;

                            Log.d(TAG, "🔄 Música REAL do Service: " + realCurrentSong.getTitle() +
                                    " | Índice: " + currentSongIndex);
                        }

                        onSongChanged(currentSong);

                        Log.d(TAG, "🔄 Música alterada: " + currentSong.getTitle() +
                                " | Índice: " + currentSongIndex + " | Razão: " + reason);
                    }
                }

                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    runOnUiThread(() -> {
                        view.updatePlayPauseIcon(isPlaying);
                        notifyAllPlaybackStateListeners(isPlaying);
                        Log.d(TAG, "▶️ Estado: " + isPlaying);
                    });
                }

                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    Log.d(TAG, "📻 Estado do player: " + playbackState);

                    if (playbackState == Player.STATE_ENDED) {
                        Log.d(TAG, "⏹️ Música terminou - Service cuidará da transição");

                        // ✅ CORREÇÃO: Pequeno delay e depois SINCRONIZAR com Service
                        new Handler().postDelayed(() -> {
                            if (musicService != null && musicService.getCurrentSong() != null) {
                                Song realCurrentSong = musicService.getCurrentSong();
                                currentSongIndex = findSongIndex(realCurrentSong);
                                onSongChanged(realCurrentSong);
                                Log.d(TAG, "✅ Sincronizado pós-término: " + realCurrentSong.getTitle());
                            }
                        }, 500);
                    }
                }
            });
        }
    }

    /**
     * Sincroniza estado do Presenter com estado REAL do Service
     */
    private void syncWithServiceState() {
        if (musicService != null && musicService.getCurrentSong() != null) {
            Song realCurrentSong = musicService.getCurrentSong();

            // BUSCAR índice REAL no nosso allSongs
            int realIndex = findSongIndex(realCurrentSong);

            if (realIndex != currentSongIndex) {
                Log.d(TAG, "🔄 Sincronizando: Índice " + currentSongIndex + " → " + realIndex);
                currentSongIndex = realIndex;
            }

            //  ATUALIZAR UI com dados REAIS
            updateSongInfoWithCover(realCurrentSong);

            Log.d(TAG, "✅ Estado sincronizado: " + realCurrentSong.getTitle() +
                    " | Índice: " + currentSongIndex);
        }
    }

    /**
     *  Método para lidar com o término da música SEM LOOP
     */
    /**
     * ✅ VERSÃO SEGURA: Apenas log e sincronização, SEM reprodução automática
     */
    private void handleSongCompletion() {
        Log.d(TAG, "🎵 handleSongCompletion - Apenas log (transição tratada pelo service)");

        // ✅ APENAS SINCRONIZAR - DEIXAR O SERVICE LIDAR COM A TRANSIÇÃO
        if (musicService != null && musicService.getCurrentSong() != null) {
            Song currentSong = musicService.getCurrentSong();
            currentSongIndex = findSongIndex(currentSong);
            Log.d(TAG, "🔁 Índice sincronizado pós-término: " + currentSongIndex);

            // ✅ ATUALIZAR UI APENAS
            updateSongInfoWithCover(currentSong);
        }

        // ✅ NÃO CHAMAR nextSong() NEM playSong() - isso causa loop!
        return;
    }
    // ============================================================
    // METODOS DO REPOSITORIO E UI
    // ============================================================

    private void loadSongsIfNeeded() {
        Log.d(TAG, "Verificando se precisa carregar músicas...");
        repository.loadSongsFromDeviceIfNeeded(context);
    }

    private void startProgressUpdate() {
        handler.post(updateProgressAction);
    }

    private void observeSongs() {
        repository.getAllSongs().observe(lifecycleOwner, songs -> {
            this.allSongs = new ArrayList<>(songs);

            // CORREÇÃO: Atualizar view APENAS se NÃO for PlaylistDetailsActivity
            String viewClassName = view.getClass().getSimpleName();
            boolean isPlaylistDetails = viewClassName.contains("PlaylistDetailsActivity");

            if (!isPlaylistDetails) {
                Log.d(TAG, "Contexto Main/Favoritas/Artistas - Atualizando view com " + songs.size() + " músicas");
                view.updateSongList(songs);
            } else {
                Log.d(TAG, "Contexto PlaylistDetailsActivity - Não atualizando view automaticamente (evita sobrescrever músicas da playlist)");
            }

            Log.d(TAG, "Lista interna de músicas atualizada, total: " + songs.size());
        });
    }

    private void observePlaylists() {
        repository.getAllPlaylists().observe(lifecycleOwner, playlists -> {
            loadPlaylistsWithRealCount();
        });
    }

    /**
     * Carrega playlists com contagem REAL de músicas
     */
    public void loadPlaylists() {
        Log.d(TAG, "Carregando playlists com contagem REAL de músicas...");
        loadPlaylistsWithRealCount();
    }

    /**
     * Método principal para carregar playlists com contagem real
     */
    private void loadPlaylistsWithRealCount() {
        repository.getAllPlaylists().observe(lifecycleOwner, playlists -> {
            if (playlists == null || playlists.isEmpty()) {
                Log.d(TAG, "Nenhuma playlist encontrada");
                view.updatePlaylistList(new ArrayList<>());
                return;
            }

            Log.d(TAG, "Playlists carregadas: " + playlists.size());

            final int[] playlistsProcessed = {0};
            final List<Playlist> playlistsWithRealCount = new ArrayList<>(playlists);

            for (int i = 0; i < playlists.size(); i++) {
                final int index = i;
                final Playlist playlist = playlists.get(i);

                repository.getSongsForPlaylist(playlist.getId()).observe(lifecycleOwner, songs -> {
                    playlistsWithRealCount.get(index).setSongCount(songs.size());
                    playlistsProcessed[0]++;

                    Log.d(TAG, "Playlist '" + playlist.getName() +
                            "' | ID: " + playlist.getId() +
                            " | Músicas REAIS: " + songs.size());

                    if (playlistsProcessed[0] == playlists.size()) {
                        Log.d(TAG, "Todas as playlists processadas, atualizando View");
                        view.updatePlaylistList(playlistsWithRealCount);
                    }
                });
            }
        });
    }

    /**
     * Método para debug - verificar músicas reais de uma playlist
     */
    public void debugCheckPlaylistSongs(long playlistId) {
        repository.getSongsForPlaylist(playlistId).observe(lifecycleOwner, songs -> {
            Log.w(TAG, "VERIFICAÇÃO REAL: Playlist " + playlistId +
                    " tem " + songs.size() + " músicas REAIS no banco");

            if (songs.size() > 0) {
                for (Song song : songs) {
                    Log.w(TAG, "   - " + song.getTitle() + " | " + song.getArtist() +
                            " | ID: " + song.getId());
                }
            } else {
                Log.w(TAG, "   Playlist está VAZIA no banco");
            }
        });
    }

    public void onPermissionsGranted() {
        Log.d(TAG, "Permissões concedidas, verificando se precisa carregar músicas.");
        loadSongsIfNeeded();
    }

    public void updatePlayPauseState() {
        if (musicService != null) {
            view.updatePlayPauseIcon(musicService.isPlaying());
        }
    }

    public void loadSongs() {
        repository.getAllSongs().observe(lifecycleOwner, songs -> {
            this.allSongs = new ArrayList<>(songs);
            view.updateSongList(songs);
        });
    }

    public void loadSongsByArtist(String artist) {
        repository.getSongsByArtist(artist).observe(lifecycleOwner, songs -> {
            view.updateSongList(songs);
        });
    }

    public void loadFavorites() {
        repository.getFavorites().observe(lifecycleOwner, songs -> {
            Log.d(TAG, "Favoritas carregadas: " + songs.size() + " músicas");
            view.updateSongList(songs);
        });
    }

    public void loadDownloadedSongs() {
        repository.getDownloadedSongs().observe(lifecycleOwner, songs -> {
            Log.d(TAG, "Músicas descarregadas: " + songs.size() + " músicas");
            view.updateSongList(songs);
        });
    }

    /**
     *  Carrega apenas músicas da playlist específica - VERSÃO MELHORADA
     */
    public void loadPlaylistSongs(Playlist playlist) {
        Log.d(TAG, "=== LOAD PLAYLIST SONGS ===");
        Log.d(TAG, "Carregando músicas DA PLAYLIST: '" + playlist.getName() + "' | ID: " + playlist.getId());

        new Thread(() -> {
            try {
                // ✅ CORREÇÃO: Usar método síncrono para garantir dados
                List<Song> songs = repository.getSongsForPlaylistSync(playlist.getId());
                int realCount = repository.getSongCountForPlaylistSync(playlist.getId());

                Log.d(TAG, "✅ Playlist " + playlist.getId() + " | Músicas REAIS: " + realCount);
                Log.d(TAG, "✅ Lista de músicas retornada: " + songs.size());

                // ✅ DEBUG DETALHADO
                if (songs.isEmpty()) {
                    Log.w(TAG, "⚠️ AVISO: Playlist " + playlist.getId() + " está VAZIA no banco");
                } else {
                    Log.d(TAG, "🎵 Músicas encontradas na playlist:");
                    for (Song song : songs) {
                        Log.d(TAG, "   - " + song.getTitle() + " | ID: " + song.getId());
                    }
                }

                runOnUiThread(() -> {
                    if (view != null) {
                        view.updateSongList(songs);
                        Log.d(TAG, "✅ View atualizada com " + songs.size() + " músicas");
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "❌ Erro ao carregar músicas da playlist: " + e.getMessage(), e);
            }
        }).start();
    }

    /**
     * ✅ NOVO: Método sobrecarregado para carregar por ID
     */
    public void loadPlaylistSongs(long playlistId) {
        Log.d(TAG, "=== LOAD PLAYLIST SONGS BY ID ===");
        Log.d(TAG, "Carregando músicas DA PLAYLIST: ID " + playlistId);

        new Thread(() -> {
            try {
                List<Song> songs = repository.getSongsForPlaylistSync(playlistId);
                int realCount = repository.getSongCountForPlaylistSync(playlistId);

                Log.d(TAG, "✅ Playlist " + playlistId + " | Músicas REAIS: " + realCount);
                Log.d(TAG, "✅ Lista de músicas retornada: " + songs.size());

                runOnUiThread(() -> {
                    if (view != null) {
                        view.updateSongList(songs);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "❌ Erro ao carregar músicas da playlist: " + e.getMessage(), e);
            }
        }).start();
    }

    public void setCurrentPlaylist(List<Song> playlist, int startIndex) {
        this.allSongs = new ArrayList<>(playlist);
        this.currentSongIndex = startIndex;

        if (musicService != null && isBound) {
            musicService.setPlaylist(allSongs);
            musicService.setCurrentSongIndex(currentSongIndex);
            Log.d(TAG, "Playlist definida no serviço: " + allSongs.size() + " músicas, início: " + startIndex);
        }
    }

    /**
     * Configura a playlist completa no serviço para transição automática
     */
    public void setPlaylistForAutomaticTransition(List<Song> playlist, int startIndex) {
        this.allSongs = new ArrayList<>(playlist);
        this.currentSongIndex = startIndex;

        if (musicService != null && isBound) {
            musicService.setPlaylist(allSongs);
            musicService.setCurrentSongIndex(currentSongIndex);

            // CORREÇÃO: Configurar o modo de repeat para permitir transição automática
            musicService.setRepeatMode(isRepeatMode);

            Log.d(TAG, "Playlist definida para transição automática: " + allSongs.size() + " músicas, início: " + startIndex);
        }
    }

    public void toggleShuffle() {
        isShuffleMode = !isShuffleMode;
        if (isShuffleMode) {
            Collections.shuffle(allSongs);
            Log.d(TAG, "Shuffle mode ON");
        } else {
            Log.d(TAG, "Shuffle mode OFF");
        }
        view.updatePlayPauseIcon(musicService != null && musicService.isPlaying());

        if (musicService != null) {
            musicService.setShuffleMode(isShuffleMode);
        }
    }

    public void toggleRepeat() {
        isRepeatMode = !isRepeatMode;
        if (musicService != null) {
            musicService.setRepeatMode(isRepeatMode);
            Log.d(TAG, "Repeat mode: " + (isRepeatMode ? "ON" : "OFF"));
        }
    }

    public boolean isShuffleMode() {
        return isShuffleMode;
    }

    public boolean isRepeatMode() {
        return isRepeatMode;
    }

    public boolean isPlaying() {
        return musicService != null && musicService.isPlaying();
    }

    public void searchSongs(String query) {
        if (allSongs == null) return;
        List<Song> filteredSongs = allSongs.stream()
                .filter(song -> song.getTitle().toLowerCase().contains(query.toLowerCase()) ||
                        song.getArtist().toLowerCase().contains(query.toLowerCase()))
                .collect(Collectors.toList());
        view.updateSongList(filteredSongs);
        Log.d(TAG, "Busca completada. Encontradas " + filteredSongs.size() + " músicas para: " + query);
    }

    public void toggleFavorite(Song song) {
        repository.toggleFavorite(song);
        Log.d(TAG, "Favorito alterado para: " + song.getTitle());
    }

    public void toggleDownloaded(Song song) {
        repository.toggleDownloaded(song);
        Log.d(TAG, "Download alterado para: " + song.getTitle() + " para " + song.isDownloaded());
    }

    public void createPlaylistWithName(String name) {
        repository.createPlaylist(name);
        Log.d(TAG, "Playlist criada: " + name);
    }

    public void addSongToPlaylist(long playlistId, long songId) {
        repository.addSongToPlaylist(playlistId, songId);
        Log.d(TAG, "Música " + songId + " adicionada a playlist " + playlistId);
    }

    public void navigateToArtists() {
        Intent intent = new Intent(context, ArtistsActivity.class);
        context.startActivity(intent);
        Log.d(TAG, "Navegando para ArtistsActivity");
    }

    public void navigateToDescarregadas() {
        Intent intent = new Intent(context, DescarregadasActivity.class);
        context.startActivity(intent);
        Log.d(TAG, "Navegando para DescarregadasActivity");
    }

    public void navigateToFavoritas() {
        Intent intent = new Intent(context, FavoritasActivity.class);
        context.startActivity(intent);
        Log.d(TAG, "Navegando para FavoritasActivity");
    }

    public void recognizeMusic() {
        Log.d(TAG, "Reconhecimento de música temporariamente desativado (ACRCloud pendente)");
    }

    public void createPlaylist() {
        Intent intent = new Intent(context, PlaylistsActivity.class);
        context.startActivity(intent);
        Log.d(TAG, "Navegando para CriarPlaylistActivity");
    }

    public void navigateToMain() {
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        Log.d(TAG, "Navegando para MainActivity");
    }

    public void requestPermissions() {
        List<String> missingPermissions = new ArrayList<>();
        for (String permission : new String[]{Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS}) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }
        if (!missingPermissions.isEmpty()) {
            view.requestPermissions();
            Log.d(TAG, "Solicitando permissões: " + missingPermissions);
        } else {
            Log.d(TAG, "Todas as permissões concedidas");
        }
    }

    public boolean isServiceBound() {
        return isBound;
    }

    public MusicService getMusicService() {
        return musicService;
    }

    public void setMusicService(MusicService musicService) {
        this.musicService = musicService;
        this.isBound = true;
        addPlayerListener();
        updatePlayPauseState();
    }


    /**
     * MEtodo chamado quando música muda
     */
    public void onSongChanged(Song song) {
        Log.d(TAG, "🔄 onSongChanged: " + (song != null ? song.getTitle() + " - " + song.getArtist() : "null"));

        //  SINCRONIZAR PRIMEIRO com Service
        syncWithServiceState();

        //  DEBUG: Verificar informações
        debugSongInfo(song);

        // ATUALIZAR UI
        if (view != null && song != null) {
            view.updateSongInfo(song.getTitle(), song.getArtist());
            loadAlbumArtForSong(song); // ✅ Carregar capa REAL
        }

        //  NOTIFICAR TODOS OS LISTENERS
        notifyAllSongChangeListeners(song);

        Log.d(TAG, " Musica alterada e sincronizada: " + song.getTitle());
    }

    /**
     * Metodo para notificar mudança de estado de reprodução
     */
    public void onPlaybackStateChanged(boolean isPlaying) {
        runOnUiThread(() -> {
            // ✅ ATUALIZAR VIEW ATUAL
            view.updatePlayPauseIcon(isPlaying);

            // ✅ NOTIFICAR TODOS OS LISTENERS
            notifyAllPlaybackStateListeners(isPlaying);

            Log.d(TAG, "▶️ Estado de reprodução alterado para todos: " + isPlaying);
        });
    }
    /**
     * Extrai metadados completos do arquivo de música
     */
    private void extractAndUpdateMusicMetadata(Song song) {
        if (song == null || song.getPath() == null) return;

        MediaMetadataRetriever retriever = null;
        try {
            retriever = new MediaMetadataRetriever();
            retriever.setDataSource(song.getPath());

            // ✅ EXTRAIR TODOS OS METADADOS DISPONÍVEIS
            String extractedTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            String extractedArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            String extractedAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);

            Log.d(TAG, "🎵 Metadados extraídos para: " + song.getTitle());
            Log.d(TAG, "   Título extraído: " + extractedTitle);
            Log.d(TAG, "   Artista extraído: " + extractedArtist);
            Log.d(TAG, "   Álbum extraído: " + extractedAlbum);
            Log.d(TAG, "   Título atual: " + song.getTitle());
            Log.d(TAG, "   Artista atual: " + song.getArtist());

            // ✅ ATUALIZAR SONG COM METADADOS REAIS SE NECESSÁRIO
            boolean updated = false;

            if (extractedTitle != null && !extractedTitle.isEmpty() && !extractedTitle.equals(song.getTitle())) {
                song.setTitle(extractedTitle);
                Log.d(TAG, "✅ Título atualizado com metadados: " + extractedTitle);
                updated = true;
            }

            if (extractedArtist != null && !extractedArtist.isEmpty() && !extractedArtist.equals(song.getArtist())) {
                song.setArtist(extractedArtist);
                Log.d(TAG, "✅ Artista atualizado com metadados: " + extractedArtist);
                updated = true;
            }

            if (updated) {
                // ✅ ATUALIZAR NO BANCO DE DADOS
                repository.updateSong(song);
                Log.d(TAG, "✅ Metadados atualizados no banco de dados");
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Erro ao extrair metadados: " + e.getMessage());
        } finally {
            if (retriever != null) {
                try {
                    retriever.release();
                } catch (Exception e) {
                    Log.e(TAG, "Erro ao liberar retriever: " + e.getMessage());
                }
            }
        }
    }
    /**
     * Tenta atualizar qualquer MainActivity ativa
     */
    private void updateAnyActiveMainActivity(Song song) {
        try {
            // Se temos uma referência direta à MainActivity (via contexto)
            if (context instanceof MainActivity) {
                MainActivity mainActivity = (MainActivity) context;
                mainActivity.updateSongInfo(song.getTitle(), song.getArtist());
                loadAlbumArtForSong(song);
                Log.d(TAG, "🎵 MainActivity (via context) atualizada: " + song.getTitle());
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Erro ao atualizar MainActivity via context: " + e.getMessage());
        }
    }

    public void forceUIUpdate() {
        if (musicService != null && musicService.getCurrentSong() != null) {
            Song currentSong = musicService.getCurrentSong();
            updateSongInfoWithCover(currentSong);
            view.updatePlayPauseIcon(musicService.isPlaying());
        }
    }

    public boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (MusicService.class.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }

    public LiveData<List<Playlist>> getAllPlaylistsLiveData() {
        return repository.getAllPlaylists();
    }

    public void addSongToPlaylist(long playlistId, Song song) {
        repository.addSongToPlaylist(playlistId, song.getId());
        Log.d(TAG, "Música " + song.getTitle() + " adicionada a playlist " + playlistId);
    }

    public void setServicePlaylist(List<Song> playlist) {
        if (musicService != null && isBound) {
            musicService.setPlaylist(playlist);
            Log.d(TAG, "Playlist definida no serviço: " + playlist.size() + " músicas");
        }
    }

    // ============================================================
    // MÉTODOS PARA CAPAS REAIS - NOVOS
    // ============================================================

    /**
     * Extrai a capa real do arquivo de música
     */
    public Bitmap getAlbumArt(String songPath) {
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(songPath);

            byte[] albumArt = retriever.getEmbeddedPicture();
            if (albumArt != null) {
                return BitmapFactory.decodeByteArray(albumArt, 0, albumArt.length);
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao extrair capa: " + e.getMessage());
        }
        return null;
    }

    // ============================================================
    // MÉTODOS PARA OS DIÁLOGOS
    // ============================================================

    /**
     * Renomeia uma música - COM VERIFICAÇÃO DE PERMISSÃO
     */
    public boolean renameSong(Song song, String newFileName) {
        try {
            Log.d(TAG, "=== INICIANDO RENOMEACAO NO PRESENTER ===");
            Log.d(TAG, "Música: " + song.getTitle());
            Log.d(TAG, "Novo nome: " + newFileName);
            Log.d(TAG, "Caminho original: " + song.getPath());

            // VERIFICAÇÃO DE PERMISSÃO - NOVO
            if (!checkAndRequestFilePermission()) {
                Log.e(TAG, "Sem permissão para renomear arquivo");
                return false;
            }

            // Verifica se o arquivo existe
            File originalFile = new File(song.getPath());
            if (!originalFile.exists()) {
                Log.e(TAG, "Arquivo original não existe: " + song.getPath());
                return false;
            }

            // Verifica permissão de escrita
            if (!originalFile.canWrite()) {
                Log.e(TAG, "Sem permissão para escrever no arquivo: " + song.getPath());

                // Tenta solicitar permissão novamente
                if (context instanceof android.app.Activity) {
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        Toast.makeText(context,
                                "Sem permissão para modificar o arquivo. Conceda permissão de armazenamento.",
                                Toast.LENGTH_LONG).show();
                    });
                }
                return false;
            }

            // Obtém o diretório do arquivo original
            File parentDir = originalFile.getParentFile();
            if (parentDir == null) {
                Log.e(TAG, "Não foi possível obter o diretório pai do arquivo");
                return false;
            }

            // Cria o novo caminho do arquivo
            File newFile = new File(parentDir, newFileName);

            // Verifica se já existe arquivo com o novo nome
            if (newFile.exists()) {
                Log.e(TAG, "Já existe um arquivo com o nome: " + newFileName);
                return false;
            }

            // Tenta renomear o arquivo
            boolean success = originalFile.renameTo(newFile);
            Log.d(TAG, "Resultado do renameTo: " + success);

            if (success) {
                // Atualiza o caminho no objeto Song
                song.setPath(newFile.getAbsolutePath());

                // Atualiza o título (sem a extensão do arquivo)
                String titleWithoutExtension = FileUtils.getFileNameWithoutExtension(newFileName);
                song.setTitle(titleWithoutExtension);

                // Atualiza no banco de dados
                repository.updateSong(song);

                // Notifica o listener
                if (operationListener != null) {
                    operationListener.onSongRenamed(song);
                    Log.d(TAG, "Listener onSongRenamed chamado");
                } else {
                    Log.w(TAG, "OperationListener é nulo - UI não será atualizada");
                }

                Log.d(TAG, "Música renomeada com sucesso: " + song.getTitle());
                return true;
            } else {
                Log.e(TAG, "Falha ao renomear o arquivo - renameTo retornou false");
                return false;
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Erro de segurança ao renomear música: " + e.getMessage());

            // Solicita permissão
            if (context instanceof android.app.Activity) {
                ((android.app.Activity) context).runOnUiThread(() -> {
                    showPermissionDialog();
                });
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao renomear música: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Exclui uma música -  COM VERIFICAÇÃO DE PERMISSÃO
     */
    public boolean deleteSong(Song song) {
        try {
            Log.d(TAG, "=== INICIANDO EXCLUSÃO NO PRESENTER ===");
            Log.d(TAG, "Música: " + song.getTitle());
            Log.d(TAG, "Caminho: " + song.getPath());

            // VERIFICAÇÃO DE PERMISSÃO - NOVO
            if (!checkAndRequestFilePermission()) {
                Log.e(TAG, "Sem permissão para excluir arquivo");
                return false;
            }

            // Verifica se o arquivo existe
            File file = new File(song.getPath());
            if (!file.exists()) {
                Log.e(TAG, "Arquivo não existe: " + song.getPath());
                return false;
            }

            // Verifica permissão de escrita
            if (!file.canWrite()) {
                Log.e(TAG, "Sem permissão para excluir o arquivo: " + song.getPath());

                // Tenta solicitar permissão novamente
                if (context instanceof android.app.Activity) {
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        Toast.makeText(context,
                                "Sem permissão para excluir o arquivo. Conceda permissão de armazenamento.",
                                Toast.LENGTH_LONG).show();
                    });
                }
                return false;
            }

            // Exclui o arquivo físico
            boolean fileDeleted = file.delete();
            Log.d(TAG, "Arquivo físico excluído: " + fileDeleted);

            if (fileDeleted) {
                // Exclui do banco de dados
                repository.deleteSong(song);

                // Notifica o listener
                if (operationListener != null) {
                    operationListener.onSongDeleted(song);
                    Log.d(TAG, "Listener onSongDeleted chamado");
                } else {
                    Log.w(TAG, "OperationListener é nulo - UI não será atualizada");
                }

                Log.d(TAG, "Música excluída com sucesso do banco: " + song.getTitle());
                return true;
            } else {
                Log.e(TAG, "Falha ao excluir o arquivo físico: " + song.getPath());
                return false;
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Erro de segurança ao excluir música: " + e.getMessage());

            // Solicita permissão
            if (context instanceof android.app.Activity) {
                ((android.app.Activity) context).runOnUiThread(() -> {
                    showPermissionDialog();
                });
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao excluir música: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Atualiza a capa de uma música com verificação de permissão
     */
    public boolean updateSongCover(long songId, String coverPath) {
        try {
            Log.d(TAG, "=== INICIANDO ATUALIZAÇÃO DE CAPA NO PRESENTER ===");
            Log.d(TAG, "Música ID: " + songId);
            Log.d(TAG, "Caminho da capa: " + coverPath);

            // VERIFICAÇÃO DE PERMISSÃO ESPECÍFICA PARA GALERIA - CORRIGIDO
            if (!hasGalleryPermission()) {
                Log.e(TAG, "Sem permissão para acessar galeria");

                // Solicita permissão específica para galeria
                if (context instanceof android.app.Activity) {
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        showGalleryPermissionDialog();
                    });
                }
                return false;
            }

            // Verifica se o arquivo de capa existe
            File coverFile = new File(coverPath);
            if (!coverFile.exists()) {
                Log.e(TAG, "Arquivo de capa não existe: " + coverPath);
                return false;
            }

            // Verifica se é um arquivo de imagem válido
            if (!isValidImageFile(coverFile)) {
                Log.e(TAG, "Arquivo não é uma imagem válida: " + coverPath);
                return false;
            }

            // Verifica permissão de leitura do arquivo
            if (!coverFile.canRead()) {
                Log.e(TAG, "Sem permissão para ler o arquivo de capa: " + coverPath);
                return false;
            }

            Song song = repository.getSongById(songId);
            if (song != null) {
                // Remove capa anterior se existir
                removeExistingCover(song);

                // Copia a nova capa para diretório seguro do app
                String safeCoverPath = copyCoverToAppDirectory(coverFile, songId);
                if (safeCoverPath == null) {
                    Log.e(TAG, "Falha ao copiar capa para diretório seguro");
                    return false;
                }

                song.setAlbumArtUri(safeCoverPath);
                repository.updateSong(song);

                // Notifica o listener
                if (operationListener != null) {
                    operationListener.onCoverChanged();
                    Log.d(TAG, "Listener onCoverChanged chamado");
                } else {
                    Log.w(TAG, "OperationListener é nulo - UI não será atualizada");
                }

                Log.d(TAG, "Capa atualizada com sucesso para a música: " + song.getTitle());
                return true;
            }
            Log.e(TAG, "Música não encontrada para ID: " + songId);
            return false;
        } catch (SecurityException e) {
            Log.e(TAG, "Erro de segurança ao atualizar capa: " + e.getMessage());
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao atualizar capa: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Remove capa existente de uma música
     */
    public boolean removeSongCover(long songId) {
        try {
            Log.d(TAG, "=== INICIANDO REMOÇÃO DE CAPA NO PRESENTER ===");
            Log.d(TAG, "Música ID: " + songId);

            Song song = repository.getSongById(songId);
            if (song != null) {
                // Remove o arquivo de capa se existir
                String coverPath = song.getAlbumArtUri();
                if (coverPath != null) {
                    File coverFile = new File(coverPath);
                    if (coverFile.exists()) {
                        // VERIFICAÇÃO DE PERMISSÃO - NOVO
                        if (!checkAndRequestFilePermission()) {
                            Log.e(TAG, "Sem permissão para remover capa");
                            return false;
                        }

                        boolean deleted = coverFile.delete();
                        Log.d(TAG, "Arquivo de capa excluído: " + deleted);
                        if (!deleted) {
                            Log.w(TAG, "Não foi possível excluir o arquivo de capa: " + coverPath);
                        }
                    } else {
                        Log.w(TAG, "Arquivo de capa não existe: " + coverPath);
                    }
                }

                // Limpa a referência no banco de dados
                song.setAlbumArtUri(null);
                repository.updateSong(song);

                // Notifica o listener
                if (operationListener != null) {
                    operationListener.onCoverChanged();
                    Log.d(TAG, "Listener onCoverChanged chamado");
                }

                Log.d(TAG, "Capa removida da música: " + song.getTitle());
                return true;
            }
            Log.e(TAG, "Música não encontrada para ID: " + songId);
            return false;
        } catch (SecurityException e) {
            Log.e(TAG, "Erro de segurança ao remover capa: " + e.getMessage());
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao remover capa: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Verifica se o arquivo é uma imagem válida
     */
    private boolean isValidImageFile(File file) {
        try {
            String fileName = file.getName().toLowerCase();
            return fileName.endsWith(".jpg") ||
                    fileName.endsWith(".jpeg") ||
                    fileName.endsWith(".png") ||
                    fileName.endsWith(".bmp") ||
                    fileName.endsWith(".webp");
        } catch (Exception e) {
            Log.e(TAG, "Erro ao verificar tipo de arquivo: " + e.getMessage());
            return false;
        }
    }

    /**
     * Remove capa existente antes de adicionar nova
     */
    private void removeExistingCover(Song song) {
        try {
            String existingCoverPath = song.getAlbumArtUri();
            if (existingCoverPath != null) {
                File existingCover = new File(existingCoverPath);
                if (existingCover.exists() && existingCover.canWrite()) {
                    boolean deleted = existingCover.delete();
                    Log.d(TAG, "Capa anterior removida: " + deleted);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao remover capa existente: " + e.getMessage());
        }
    }

    /**
     * Copia a capa para diretório seguro do app
     */
    private String copyCoverToAppDirectory(File sourceFile, long songId) {
        try {
            // Cria diretório de capas se não existir
            File coversDir = new File(context.getFilesDir(), "covers");
            if (!coversDir.exists()) {
                boolean created = coversDir.mkdirs();
                Log.d(TAG, "Diretório de capas criado: " + created);
            }

            // Nome único para o arquivo
            String fileName = "cover_" + songId + "_" + System.currentTimeMillis() + ".jpg";
            File destFile = new File(coversDir, fileName);

            // Copia o arquivo
            try (java.io.InputStream in = new java.io.FileInputStream(sourceFile);
                 java.io.OutputStream out = new java.io.FileOutputStream(destFile)) {

                byte[] buffer = new byte[1024];
                int length;
                while ((length = in.read(buffer)) > 0) {
                    out.write(buffer, 0, length);
                }
            }

            Log.d(TAG, "Capa copiada para: " + destFile.getAbsolutePath());
            return destFile.getAbsolutePath();

        } catch (Exception e) {
            Log.e(TAG, "Erro ao copiar capa: " + e.getMessage());
            return null;
        }
    }

    /**
     * Obtém o caminho da capa personalizada de uma música
     */
    public String getSongCoverPath(long songId) {
        try {
            Song song = repository.getSongById(songId);
            if (song != null && song.getAlbumArtUri() != null) {
                String coverPath = song.getAlbumArtUri();

                // Verifica se o arquivo ainda existe
                File coverFile = new File(coverPath);
                if (coverFile.exists()) {
                    Log.d(TAG, "Capa encontrada para música " + songId + ": " + coverPath);
                    return coverPath;
                } else {
                    Log.w(TAG, "Arquivo de capa não existe mais: " + coverPath);
                    // Remove referência inválida
                    song.setAlbumArtUri(null);
                    repository.updateSong(song);
                }
            }
            Log.d(TAG, "Nenhuma capa personalizada encontrada para música " + songId);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Erro ao obter capa: " + e.getMessage());
            return null;
        }
    }

    // ============================================================
    // INTERFACE E LISTENER
    // ============================================================

    /**
     * Interface para callback das operações
     */
    public interface OnMusicOperationListener {
        void onSongDeleted(Song song);
        void onSongRenamed(Song song);
        void onCoverChanged();
    }

    /**
     * Configura o listener para operações - MÉTODO ESSENCIAL
     */
    public void setOperationListener(OnMusicOperationListener listener) {
        this.operationListener = listener;
        Log.d(TAG, "OperationListener configurado: " + (listener != null));
    }

    /**
     * Força sincronização completa com o serviço
     */
    public void forceServiceSync() {
        if (musicService != null && isBound) {
            // Atualizar UI com estado atual do serviço
            Song currentSong = musicService.getCurrentSong();
            if (currentSong != null) {
                updateSongInfoWithCover(currentSong);
                view.updatePlayPauseIcon(musicService.isPlaying());
                view.updateProgress((int) musicService.getCurrentPosition(),
                        (int) musicService.getDuration());
            }
            Log.d(TAG, "Sincronização forçada com serviço realizada");
        }
    }

    // ============================================================
    // MÉTODOS PARA DIALOG ADICIONAR NA PLAYLIST
    // ============================================================

    /**
     * ADICIONAR MÚSICA À PLAYLIST COM CALLBACK
     */
    public void addSongToPlaylist(long playlistId, Song song, OnPlaylistOperationListener listener) {
        Log.d(TAG, "=== ADIÇÃO À PLAYLIST COM CALLBACK ===");
        Log.d(TAG, "Playlist ID: " + playlistId);
        Log.d(TAG, "Música: " + song.getTitle() + " (ID: " + song.getId() + ")");

        repository.addSongToPlaylist(playlistId, song.getId());

        // ✅ VERIFICAR SE FOI ADICIONADA
        new android.os.Handler().postDelayed(() -> {
            repository.getSongsForPlaylist(playlistId).observe(lifecycleOwner, songs -> {
                boolean added = false;
                for (Song s : songs) {
                    if (s.getId() == song.getId()) {
                        added = true;
                        break;
                    }
                }

                if (added) {
                    Log.d(TAG, "✅ CONFIRMADO: Música foi adicionada à playlist");
                    if (listener != null) {
                        listener.onSuccess();
                    }
                } else {
                    Log.e(TAG, "❌ FALHA: Música NÃO foi adicionada à playlist");
                    if (listener != null) {
                        listener.onError("Falha ao adicionar música");
                    }
                }
            });
        }, 500);
    }

    /**
     * INTERFACE PARA CALLBACK DA OPERAÇÃO
     */
    public interface OnPlaylistOperationListener {
        void onSuccess();
        void onError(String error);
    }

    /**
     * OBTER MÚSICAS DE UMA PLAYLIST (para verificação)
     */
    public void getSongsForPlaylist(long playlistId, androidx.lifecycle.Observer<List<Song>> observer) {
        repository.getSongsForPlaylist(playlistId).observe(lifecycleOwner, observer);
    }

    /**
     *  DEBUG: Verificar adição à playlist
     */
    public void debugCheckPlaylistAddition(long playlistId, long songId) {
        Log.d(TAG, "=== DEBUG ADIÇÃO À PLAYLIST ===");
        Log.d(TAG, "Playlist ID: " + playlistId);
        Log.d(TAG, "Song ID: " + songId);

        // Verificar se a relação existe no banco
        new Thread(() -> {
            try {
                // Verificar diretamente no banco
                List<Song> songsInPlaylist = repository.getSongsForPlaylistSync(playlistId);
                Log.d(TAG, "Músicas na playlist " + playlistId + ": " + songsInPlaylist.size());

                for (Song song : songsInPlaylist) {
                    Log.d(TAG, " - " + song.getTitle() + " (ID: " + song.getId() + ")");
                    if (song.getId() == songId) {
                        Log.d(TAG, "✅ MÚSICA ENCONTRADA NA PLAYLIST!");
                    }
                }

                if (songsInPlaylist.isEmpty()) {
                    Log.e(TAG, "❌ PLAYLIST ESTÁ VAZIA NO BANCO!");
                }

            } catch (Exception e) {
                Log.e(TAG, "Erro ao verificar playlist: " + e.getMessage());
            }
        }).start();
    }

    /**
     *  VERIFICA SE UMA MÚSICA JÁ ESTÁ NA PLAYLIST
     */
    public void checkIfSongInPlaylist(long playlistId, long songId, OnPlaylistCheckListener listener) {
        new Thread(() -> {
            try {
                // Verificar diretamente no banco
                List<Song> songsInPlaylist = repository.getSongsForPlaylistSync(playlistId);
                boolean alreadyExists = false;

                for (Song song : songsInPlaylist) {
                    if (song.getId() == songId) {
                        alreadyExists = true;
                        break;
                    }
                }

                if (listener != null) {
                    if (alreadyExists) {
                        listener.onAlreadyInPlaylist();
                    } else {
                        listener.onNotInPlaylist();
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "Erro ao verificar música na playlist: " + e.getMessage());
                if (listener != null) {
                    listener.onError("Erro ao verificar playlist");
                }
            }
        }).start();
    }

    /**
     *  INTERFACE PARA VERIFICAÇÃO
     */
    public interface OnPlaylistCheckListener {
        void onAlreadyInPlaylist();
        void onNotInPlaylist();
        void onError(String error);
    }

    /**
     *  Obter todas as playlists de forma síncrona
     */
    public List<Playlist> getAllPlaylistsSync() {
        try {
            return repository.getAllPlaylistsSync();
        } catch (Exception e) {
            Log.e(TAG, " Erro ao obter playlists sincronamente: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    // ============================================================
    //  MÉTODOS PARA PLAYERACTIVITY
    // ============================================================

    /**
     *  METODO CORRIGIDO: Reproduz uma playlist completa
     */
    public void playPlaylist(List<Song> playlist, int startPosition) {
        if (playlist == null || playlist.isEmpty()) {
            Log.e(TAG, "❌ Playlist vazia ou nula");
            return;
        }

        Log.d(TAG, "🎵 Reproduzindo playlist: " + playlist.size() + " músicas, posição: " + startPosition);

        //  ATUALIZAR LISTA INTERNA
        this.allSongs = new ArrayList<>(playlist);
        this.currentSongIndex = startPosition;

        if (startPosition < 0 || startPosition >= playlist.size()) {
            Log.w(TAG, "⚠️ Posição inicial inválida, usando 0");
            currentSongIndex = 0;
        }

        //  REPRODUZIR VIA SERVICE
        if (musicService != null && isBound) {
            musicService.setPlaylist(allSongs);
            musicService.setCurrentSongIndex(currentSongIndex);

            Song startSong = allSongs.get(currentSongIndex);
            musicService.playSong(startSong);

            //  ATUALIZAR UI
            updateSongInfoWithCover(startSong);
            view.updatePlayPauseIcon(true);

            Log.d(TAG, "✅ Playlist iniciada no service: " + startSong.getTitle());
        } else {
            //  FALLBACK: REPRODUZIR DIRETAMENTE
            Log.w(TAG, "Service não disponível, reproduzindo diretamente");
            if (currentSongIndex < playlist.size()) {
                Song startSong = playlist.get(currentSongIndex);
                playSong(startSong);
            }
        }
    }

    /**
     *  Busca música por ID (SÍNCRONO)
     */
    public Song getSongById(long songId) {
        try {
            Log.d(TAG, "🔍 Buscando música por ID: " + songId);

            // ✅ BUSCAR NO REPOSITÓRIO
            Song song = repository.getSongById(songId);

            if (song != null) {
                Log.d(TAG, "✅ Música encontrada: " + song.getTitle() + " (ID: " + songId + ")");
            } else {
                Log.w(TAG, "⚠️ Música não encontrada: ID " + songId);
            }

            return song;

        } catch (Exception e) {
            Log.e(TAG, "❌ Erro ao buscar música por ID: " + songId + " - " + e.getMessage());
            return null;
        }
    }

    // ============================================================
    // MÉTODOS PARA CAPAS REAIS
    // ============================================================

    /**
     *  MÉTODO PRINCIPAL: Carrega capa real para uma música específica
     */
    public void loadAlbumArtForSong(Song song) {
        if (song == null || view == null) {
            Log.w(TAG, "❌ Song ou View nula - não é possível carregar capa");
            return;
        }

        Log.d(TAG, "🎨 Solicitando capa para: " + song.getTitle());

        executor.execute(() -> {
            try {
                // ✅ TENTAR EXTRAIR CAPA DO ARQUIVO DE MÚSICA
                Bitmap albumArt = extractAlbumArtFromFile(song.getPath());

                runOnUiThread(() -> {
                    if (albumArt != null) {
                        // ✅ CAPA ENCONTRADA - ATUALIZAR UI
                        Log.d(TAG, "✅ Capa real encontrada: " + song.getTitle() +
                                " - " + albumArt.getWidth() + "x" + albumArt.getHeight());
                        view.updateAlbumArt(albumArt);
                    } else {
                        // ✅ NENHUMA CAPA ENCONTRADA - USAR CAPA PERSONALIZADA
                        Log.d(TAG, "🎨 Nenhuma capa embutida, tentando capa personalizada...");
                        loadCustomAlbumArt(song);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "❌ Erro ao extrair capa de " + song.getTitle() + ": " + e.getMessage());
                runOnUiThread(() -> {
                    loadCustomAlbumArt(song); // Fallback para capa personalizada
                });
            }
        });
    }

    /**
     * ✅ EXTRAI CAPA DO ARQUIVO DE MÚSICA
     */
    private Bitmap extractAlbumArtFromFile(String filePath) {
        MediaMetadataRetriever retriever = null;
        try {
            retriever = new MediaMetadataRetriever();
            retriever.setDataSource(filePath);

            byte[] albumArtData = retriever.getEmbeddedPicture();
            if (albumArtData != null) {
                Bitmap bitmap = BitmapFactory.decodeByteArray(albumArtData, 0, albumArtData.length);

                // ✅ OTIMIZAR BITMAP PARA EVITAR OUT OF MEMORY
                if (bitmap != null) {
                    int maxSize = 512; // Tamanho máximo para economizar memória
                    int width = bitmap.getWidth();
                    int height = bitmap.getHeight();

                    if (width > maxSize || height > maxSize) {
                        float ratio = (float) width / height;
                        int newWidth, newHeight;

                        if (width > height) {
                            newWidth = maxSize;
                            newHeight = (int) (maxSize / ratio);
                        } else {
                            newHeight = maxSize;
                            newWidth = (int) (maxSize * ratio);
                        }

                        bitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
                        Log.d(TAG, "🖼️ Bitmap redimensionado: " + newWidth + "x" + newHeight);
                    }
                }

                return bitmap;
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Erro no MediaMetadataRetriever: " + e.getMessage());
        } finally {
            if (retriever != null) {
                try {
                    retriever.release();
                } catch (Exception e) {
                    Log.e(TAG, "Erro ao liberar retriever: " + e.getMessage());
                }
            }
        }
        return null;
    }

    /**
     *  CARREGA CAPA PERSONALIZADA (se existir)
     */
    private void loadCustomAlbumArt(Song song) {
        try {
            String customCoverPath = getSongCoverPath(song.getId());

            if (customCoverPath != null) {
                //  CAPA PERSONALIZADA ENCONTRADA
                File coverFile = new File(customCoverPath);
                if (coverFile.exists()) {
                    Bitmap customBitmap = BitmapFactory.decodeFile(customCoverPath);
                    if (customBitmap != null) {
                        Log.d(TAG, " Capa personalizada carregada: " + customCoverPath);
                        view.updateAlbumArt(customBitmap);
                        return;
                    }
                } else {
                    Log.w(TAG, " Arquivo de capa personalizada não existe: " + customCoverPath);
                    // Remove referência inválida
                    removeSongCover(song.getId());
                }
            }

            //  NENHUMA CAPA ENCONTRADA - USAR PLACEHOLDER
            Log.d(TAG, "🎨 Usando placeholder para: " + song.getTitle());
            view.updateAlbumArt((Bitmap) null); // Isso deve carregar o placeholder na View

        } catch (Exception e) {
            Log.e(TAG, "❌ Erro ao carregar capa personalizada: " + e.getMessage());
            view.updateAlbumArt((Bitmap) null); // Fallback final
        }
    }

    /**
     *  METODO SOBRECARREGADO: Carrega capa a partir do URI
     */
    public void loadAlbumArtFromUri(String albumArtUri) {
        if (albumArtUri == null || albumArtUri.isEmpty()) {
            Log.w(TAG, "❌ URI de capa vazia");
            view.updateAlbumArt((Bitmap) null);
            return;
        }

        executor.execute(() -> {
            try {
                Bitmap bitmap;

                if (albumArtUri.startsWith("content://") || albumArtUri.startsWith("file://")) {
                    // ✅ CARREGAR DE URI DO ANDROID
                    bitmap = loadBitmapFromUri(Uri.parse(albumArtUri));
                } else {
                    // ✅ CARREGAR DE CAMINHO DE ARQUIVO
                    bitmap = BitmapFactory.decodeFile(albumArtUri);
                }

                runOnUiThread(() -> {
                    if (bitmap != null) {
                        Log.d(TAG, "✅ Capa carregada do URI: " + albumArtUri);
                        view.updateAlbumArt(bitmap);
                    } else {
                        Log.w(TAG, "❌ Não foi possível carregar capa do URI: " + albumArtUri);
                        view.updateAlbumArt((Bitmap) null);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "❌ Erro ao carregar capa do URI: " + e.getMessage());
                runOnUiThread(() -> view.updateAlbumArt((Bitmap) null));
            }
        });
    }

    /**
     *  CARREGA BITMAP A PARTIR DE URI DO ANDROID
     */
    private Bitmap loadBitmapFromUri(Uri uri) {
        try {
            android.content.ContentResolver resolver = context.getContentResolver();
            return BitmapFactory.decodeStream(resolver.openInputStream(uri));
        } catch (Exception e) {
            Log.e(TAG, "❌ Erro ao carregar bitmap da URI: " + e.getMessage());
            return null;
        }
    }


    /**
     * Atualiza informações da música incluindo capa real
     */
    public void updateSongInfoWithCover(Song song) {
        if (view != null && song != null) {
            // ✅ DEBUG: Verificar informações antes de atualizar
            debugSongInfo(song);

            Log.d(TAG, "🎵 updateSongInfoWithCover: " + song.getTitle() + " - " + song.getArtist());

            // ✅ CORREÇÃO: Garantir que ambos título e artista sejam atualizados
            view.updateSongInfo(song.getTitle(), song.getArtist());

            // Buscar capa real da música
            Bitmap albumArt = getAlbumArt(song.getPath());

            // Se a view for MainActivity, chama o método específico de capa
            if (view instanceof MainActivity) {
                ((MainActivity) view).updateAlbumArt(albumArt);
            }

            Log.d(TAG, "✅ Informações atualizadas: " + song.getTitle() + " - " + song.getArtist());
        }
    }
    /**
     *  METODO DE DEBUG: Verificar informações antes de atualizar a view
     */
    private void debugSongInfo(Song song) {
        if (song != null) {
            Log.d(TAG, "🔍 DEBUG Song Info:");
            Log.d(TAG, "   Título: " + song.getTitle());
            Log.d(TAG, "   Artista: " + song.getArtist());
            Log.d(TAG, "   Path: " + song.getPath());
            Log.d(TAG, "   ID: " + song.getId());
        } else {
            Log.d(TAG, "🔍 DEBUG: Song é nulo");
        }
    }

    /**
     *  METODO NOVO: Busca música por ID (ASSÍNCRONO com callback)
     */
    public void getSongByIdAsync(long songId, OnSongLoadedListener listener) {
        new Thread(() -> {
            try {
                Song song = repository.getSongById(songId);

                runOnUiThread(() -> {
                    if (song != null && listener != null) {
                        listener.onSongLoaded(song);
                    } else if (listener != null) {
                        listener.onError("Música não encontrada: ID " + songId);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Erro ao buscar música async: " + e.getMessage());
                runOnUiThread(() -> {
                    if (listener != null) {
                        listener.onError("Erro: " + e.getMessage());
                    }
                });
            }
        }).start();
    }

    /**
     *  INTERFACE para callback de carregamento de música
     */
    public interface OnSongLoadedListener {
        void onSongLoaded(Song song);
        void onError(String error);
    }

    /**
     *  METODO MELHORADO: Carrega múltiplas músicas por IDs
     */
    public void loadSongsByIds(List<Long> songIds, OnSongsLoadedListener listener) {
        if (songIds == null || songIds.isEmpty()) {
            if (listener != null) listener.onError("Lista de IDs vazia");
            return;
        }

        new Thread(() -> {
            try {
                List<Song> loadedSongs = new ArrayList<>();

                for (Long songId : songIds) {
                    Song song = repository.getSongById(songId);
                    if (song != null) {
                        loadedSongs.add(song);
                        Log.d(TAG, "✅ Carregada: " + song.getTitle() + " (ID: " + songId + ")");
                    } else {
                        Log.w(TAG, "⚠️ Música não encontrada: ID " + songId);
                    }
                }

                runOnUiThread(() -> {
                    if (listener != null) {
                        if (!loadedSongs.isEmpty()) {
                            listener.onSongsLoaded(loadedSongs);
                        } else {
                            listener.onError("Nenhuma música encontrada");
                        }
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Erro ao carregar múltiplas músicas: " + e.getMessage());
                runOnUiThread(() -> {
                    if (listener != null) listener.onError("Erro: " + e.getMessage());
                });
            }
        }).start();
    }

    /**
     *  INTERFACE para callback de múltiplas músicas
     */
    public interface OnSongsLoadedListener {
        void onSongsLoaded(List<Song> songs);
        void onError(String error);
    }

    // ============================================================
    // CICLO DE VIDA
    // ============================================================

    public void onDestroy() {
        Log.d(TAG, "Presenter onDestroy chamado");

        handler.removeCallbacks(updateProgressAction);

        if (isBound) {
            try {
                context.unbindService(serviceConnection);
                isBound = false;
                musicService = null;
                Log.d(TAG, "Desconectado do MusicService");
            } catch (Exception e) {
                Log.e(TAG, "Erro ao desconectar do service: " + e.getMessage());
            }
        }

        //  LIMPAR LISTA DE LISTENERS
        songChangeListeners.clear();
        Log.d(TAG, "Listeners limpos: " + songChangeListeners.size());
    }

    // ============================================================
    // MÉTODOS DEPRECIADOS
    // ============================================================

    @Deprecated
    public void getSongs(MusicView view) {
        // Obsoleto - usar loadSongs()
    }

    public List<Song> getSongList() {
        return allSongs;
    }

    public void loadArtists() {
        repository.getAllSongs().observe(lifecycleOwner, songs -> {
            List<String> artists = songs.stream()
                    .map(Song::getArtist)
                    .distinct()
                    .collect(Collectors.toList());
            view.updateArtists(artists);
            Log.d(TAG, "Carregados " + artists.size() + " artistas");
        });
    }

    public LiveData<List<String>> getAllArtists() {
        MutableLiveData<List<String>> artistsLiveData = new MutableLiveData<>();
        repository.getAllSongs().observe(lifecycleOwner, songs -> {
            List<String> artists = songs.stream()
                    .map(Song::getArtist)
                    .distinct()
                    .collect(Collectors.toList());
            artistsLiveData.setValue(artists);
        });
        return artistsLiveData;
    }

    // ============================================================
    // MÉTODOS AUXILIARES DE UI
    // ============================================================

    private void runOnUiThread(Runnable action) {
        if (context instanceof android.app.Activity) {
            ((android.app.Activity) context).runOnUiThread(action);
        } else {
            new Handler().post(action);
        }
    }

    /**
     * METODO PARA Expor LiveData para observação
     */
    public LiveData<List<Song>> getPlaylistSongsLiveData(long playlistId) {
        Log.d(TAG, "🎵 EXPONDO LiveData para playlist ID: " + playlistId);
        return repository.getSongsForPlaylist(playlistId);
    }

    /**
     *  METODO ALTERNATIVO: Para observar diretamente
     */
    public void observePlaylistSongs(long playlistId, androidx.lifecycle.Observer<List<Song>> observer) {
        Log.d(TAG, "🎵 CONFIGURANDO OBSERVAÇÃO DIRETA para playlist ID: " + playlistId);
        repository.getSongsForPlaylist(playlistId).observe(lifecycleOwner, observer);
    }
}