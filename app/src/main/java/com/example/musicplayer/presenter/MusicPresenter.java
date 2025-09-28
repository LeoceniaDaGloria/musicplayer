package com.example.musicplayer.presenter;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.musicplayer.db.MusicRepository;
import com.example.musicplayer.model.Playlist;
import com.example.musicplayer.model.Song;
import com.example.musicplayer.services.MusicService;
import com.example.musicplayer.services.UpdateMusicWorker;
import com.example.musicplayer.view.ArtistsActivity;
import com.example.musicplayer.view.CriarPlaylistActivity;
import com.example.musicplayer.view.DescarregadasActivity;
import com.example.musicplayer.view.FavoritasActivity;
import com.example.musicplayer.view.MainActivity;
import com.example.musicplayer.view.MusicView;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Presenter na arquitetura MVP para gerenciar a lógica de negócios da reprodução de música.
 * Interage com o repositório (modelo) e a view.
 */
public class MusicPresenter {
    private static final String TAG = "MusicPresenter";
    private final MusicView view;
    private final MusicRepository repository;
    private MusicService musicService;
    private boolean isBound = false;
    private final Context context;
    private final LifecycleOwner lifecycleOwner;
    private List<Song> allSongs = new ArrayList<>(); // Cache de todas as músicas
    private int currentSongIndex = -1;
    private boolean isShuffleMode = false;
    private boolean isRepeatMode = false;
    private final Handler handler = new Handler();
    private final Runnable updateProgressAction = new Runnable() {
        @Override
        public void run() {
            if (musicService != null && musicService.isPlaying()) {
                view.updateProgress((int) musicService.getCurrentPosition(), (int) musicService.getDuration());
            }
            handler.postDelayed(this, 1000);
        }
    };

    public MusicPresenter(MusicView view, Context context, LifecycleOwner lifecycleOwner) {
        this.view = view;
        this.context = context;
        this.lifecycleOwner = lifecycleOwner;
        this.repository = new MusicRepository(context);
        repository.loadSongsFromDevice(context); // Inicializa o carregamento de músicas
        observeSongs();
        observePlaylists();
        startProgressUpdate();
        insertInitialData(); // Inserir dados iniciais para teste
    }

    private void startProgressUpdate() {
        handler.post(updateProgressAction);
    }

    public void addPlayerListener() {
        if (musicService != null && musicService.getPlayer() != null) {
            musicService.getPlayer().addListener(new Player.Listener() {
                @Override
                public void onMediaItemTransition(com.google.android.exoplayer2.MediaItem mediaItem, int reason) {
                    if (musicService.getCurrentSong() != null) {
                        onSongChanged(musicService.getCurrentSong());
                        currentSongIndex = allSongs.indexOf(musicService.getCurrentSong());
                    }
                }
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_ENDED) {
                        nextSong();
                    }
                }
                @Override
                public void onPlayerError(PlaybackException error) {
                    Log.e(TAG, "Player error: " + error.getMessage());
                    nextSong(); // Pular para a próxima música em caso de erro
                }
            });
        }
    }

    private void observeSongs() {
        repository.getAllSongs().observe(lifecycleOwner, songs -> {
            this.allSongs = new ArrayList<>(songs); // Atualiza cache
            view.updateSongList(songs);
            Log.d(TAG, "Songs list updated, total: " + songs.size());
        });
    }

    private void observePlaylists() {
        repository.getAllPlaylists().observe(lifecycleOwner, playlists -> {
            view.updatePlaylistList(playlists);
        });
    }

    public void setMusicService(MusicService musicService) {
        this.musicService = musicService;
        this.isBound = true;
        addPlayerListener();
        updatePlayPauseState();
    }

    public MusicService getMusicService() {
        return musicService;
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

    public void loadPlaylists() {
        repository.getAllPlaylists().observe(lifecycleOwner, playlists -> {
            view.updatePlaylistList(playlists);
        });
    }

    public void loadFavorites() {
        repository.getFavorites().observe(lifecycleOwner, songs -> {
            view.updateSongList(songs);
        });
    }

    public void loadDownloadedSongs() {
        repository.getAllSongs().observe(lifecycleOwner, songs -> {
            List<Song> downloaded = songs.stream()
                    .filter(song -> song.getPath().startsWith("file://"))
                    .collect(Collectors.toList());
            view.updateSongList(downloaded);
        });
    }

    public void loadPlaylistSongs(Playlist playlist) {
        repository.getSongsForPlaylist(playlist.getId()).observe(lifecycleOwner, songs -> {
            view.updateSongList(songs);
        });
    }

    public void playSong(Song song) {
        if (musicService != null) {
            musicService.playSong(song);
            view.updateSongInfo(song.getTitle(), song.getArtist());
            view.updatePlayPauseIcon(true);
            currentSongIndex = allSongs.indexOf(song);
            if (currentSongIndex < 0) {
                allSongs.add(song); // Adiciona se não estiver no cache
                currentSongIndex = allSongs.size() - 1;
            }
        }
    }

    public void playPause() {
        if (musicService != null) {
            musicService.playPause();
            view.updatePlayPauseIcon(musicService.isPlaying());
        }
    }

    public void prevSong() {
        if (allSongs == null || allSongs.isEmpty()) return;
        if (musicService != null && musicService.getCurrentPosition() > 3000) {
            musicService.seekTo(0);
            view.updateProgress(0, (int) musicService.getDuration()); // Atualiza UI
            return;
        }
        if (isShuffleMode) {
            currentSongIndex = new Random().nextInt(allSongs.size());
        } else {
            currentSongIndex = (currentSongIndex - 1 + allSongs.size()) % allSongs.size();
        }
        playSong(allSongs.get(currentSongIndex));
    }

    public void nextSong() {
        if (allSongs == null || allSongs.isEmpty()) return;
        if (isShuffleMode) {
            currentSongIndex = new Random().nextInt(allSongs.size());
        } else {
            currentSongIndex = (currentSongIndex + 1) % allSongs.size();
        }
        playSong(allSongs.get(currentSongIndex));
    }

    public void toggleShuffle() {
        isShuffleMode = !isShuffleMode;
        if (isShuffleMode) Collections.shuffle(allSongs);
        view.updatePlayPauseIcon(musicService != null && musicService.isPlaying()); // Atualiza UI
    }

    public void toggleRepeat() {
        isRepeatMode = !isRepeatMode;
        if (musicService != null) {
            musicService.setRepeatMode(isRepeatMode);
        }
    }

    public void searchSongs(String query) {
        if (allSongs == null) return;
        List<Song> filteredSongs = allSongs.stream()
                .filter(song -> song.getTitle().toLowerCase().contains(query.toLowerCase()) ||
                        song.getArtist().toLowerCase().contains(query.toLowerCase()))
                .collect(Collectors.toList());
        view.updateSongList(filteredSongs);
    }

    public void seekTo(int progress) {
        if (musicService != null) {
            musicService.seekTo(progress);
            view.updateProgress(progress, (int) musicService.getDuration()); // Atualiza UI
        }
    }

    public void toggleFavorite(Song song) {
        repository.toggleFavorite(song);
    }

    public void createPlaylistWithName(String name) {
        repository.createPlaylist(name);
    }

    public void addSongToPlaylist(long playlistId, long songId) {
        repository.addSongToPlaylist(playlistId, songId); // Usa long diretamente
    }

    public void navigateToArtists() {
        Intent intent = new Intent(context, ArtistsActivity.class);
        context.startActivity(intent);
    }

    public void navigateToDescarregadas() {
        Intent intent = new Intent(context, DescarregadasActivity.class);
        context.startActivity(intent);
    }

    public void navigateToFavoritas() {
        Intent intent = new Intent(context, FavoritasActivity.class);
        context.startActivity(intent);
    }

    public void recognizeMusic() {
        Log.d(TAG, "Reconhecimento de música temporariamente desativado (ACRCloud pendente)");
    }

    public void createPlaylist() {
        Intent intent = new Intent(context, CriarPlaylistActivity.class);
        context.startActivity(intent);
    }

    public void navigateToMain() {
        Intent intent = new Intent(context, MainActivity.class);
        context.startActivity(intent);
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
        }
    }

    public boolean isServiceBound() {
        return isBound;
    }

    public void onDestroy() {
        handler.removeCallbacks(updateProgressAction);
    }

    @Deprecated
    public void getSongs(MusicView view) {
        // Obsoleto
    }

    @Deprecated
    public List<Song> getSongs() {
        return allSongs;
    }

    public void onSongChanged(Song song) {
        view.updateSongInfo(song.getTitle(), song.getArtist());
    }

    public void insertInitialData() {
        if (repository.getAllSongs().getValue() == null || repository.getAllSongs().getValue().isEmpty()) {
            Song song = new Song("Sample Song", "Sample Artist", "file:///android_asset/sample.mp3", 180000);
            Playlist playlist = new Playlist(0, "Default Playlist"); // Usar 0 para autoGenerate
            repository.insertSong(song);
            repository.insertPlaylist(playlist);
            repository.addSongToPlaylist(playlist.getId(), song.getId());
            Log.d(TAG, "Initial data inserted");
        }
    }

    public List<Song> getSongList() {
        return allSongs;
    }

    public void schedulePlaybackAlarm(long time) {
        if (musicService != null && currentSongIndex >= 0) {
            Song song = allSongs.get(currentSongIndex);
            Intent intent = new Intent(context, MusicService.class);
            intent.setAction("play");
            intent.putExtra("song_path", song.getPath());
            intent.putExtra("song_title", song.getTitle());
            intent.putExtra("song_artist", song.getArtist());
            PendingIntent pendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, time, pendingIntent);
                Log.d(TAG, "Playback scheduled at " + time);
            }
        }
    }

    public void scheduleMusicUpdate() {
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                UpdateMusicWorker.class, 15, TimeUnit.MINUTES)
                .build();
        WorkManager.getInstance(context).enqueue(workRequest);
        Log.d(TAG, "Music update scheduled");
    }

    public void loadArtists() {
        repository.getAllSongs().observe(lifecycleOwner, songs -> {
            List<String> artists = songs.stream()
                    .map(Song::getArtist)
                    .distinct()
                    .collect(Collectors.toList());
            // Removido view.updateArtists(artists); pois a ArtistsActivity gerencia isso via observe
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
}