package com.example.musicplayer.db;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.room.Room;

import com.example.musicplayer.model.Playlist;
import com.example.musicplayer.model.PlaylistSong;
import com.example.musicplayer.model.Song;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Repositório para gerenciar dados de músicas e playlists.
 * Lida com Room para armazenamento local e MediaStore para carregar músicas do dispositivo.
 */
public class MusicRepository {
    private static final String TAG = "MusicRepository";
    private AppDatabase db; // Instância do banco de dados Room
    private SongDao songDao; // DAO para músicas
    private PlaylistDao playlistDao; // DAO para playlists

    /**
     * Construtor do repositório.
     * Inicializa o banco de dados Room.
     * @param context Contexto da aplicação.
     */
    public MusicRepository(Context context) {
        if (context == null) {
            Log.e(TAG, "Context is null!");
            return;
        }
        db = Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, "music_db")
                .fallbackToDestructiveMigration()
                .build();
        songDao = db.songDao();
        playlistDao = db.playlistDao();
        Log.d(TAG, "Repository initialized");
    }

    /**
     * Retorna todas as músicas como LiveData.
     * @return LiveData com lista de músicas.
     */
    public LiveData<List<Song>> getAllSongs() {
        return songDao.getAll();
    }

    /**
     * Retorna músicas de uma playlist específica como LiveData.
     * @param playlistId ID da playlist.
     * @return LiveData com lista de músicas da playlist.
     */
    public LiveData<List<Song>> getSongsForPlaylist(long playlistId) {
        return playlistDao.getSongsForPlaylist(playlistId);
    }

    /**
     * Retorna todas as playlists como LiveData.
     * @return LiveData com lista de playlists.
     */
    public LiveData<List<Playlist>> getAllPlaylists() {
        return playlistDao.getAll();
    }

    /**
     * Insere uma música no banco de dados de forma assíncrona.
     * @param song Música a ser inserida.
     */
    public void insertSong(Song song) {
        Executors.newSingleThreadExecutor().execute(() -> songDao.insert(song));
    }

    /**
     * Insere uma playlist no banco de dados de forma assíncrona.
     * @param playlist Playlist a ser inserida.
     */
    public void insertPlaylist(Playlist playlist) {
        Executors.newSingleThreadExecutor().execute(() -> playlistDao.insert(playlist));
    }

    /**
     * Insere uma relação entre playlist e música no banco de dados de forma assíncrona.
     * @param playlistSong Relação a ser inserida.
     */
    public void insertPlaylistSong(PlaylistSong playlistSong) {
        Executors.newSingleThreadExecutor().execute(() -> playlistDao.insertPlaylistSong(playlistSong));
    }

    /**
     * Carrega músicas do MediaStore do dispositivo e insere no banco de dados.
     * Verifica permissões antes de prosseguir.
     * @param context Contexto da aplicação.
     */
    public void loadSongsFromDevice(Context context) {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ?
                Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission " + permission + " not granted, cannot load songs");
            return;
        }

        Log.d(TAG, "Starting to load songs from MediaStore with permission: " + permission);
        ContentResolver contentResolver = context.getContentResolver();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DURATION
        };
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        Cursor cursor = null;

        try {
            cursor = contentResolver.query(uri, projection, selection, null, null);
            if (cursor == null) {
                Log.e(TAG, "Cursor is null, no songs found or MediaStore access failed");
                return;
            }

            List<Song> songs = new ArrayList<>();
            while (cursor.moveToNext()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID));
                String title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE));
                String artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST));
                String data = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));
                int duration = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION));
                Song song = new Song(id, title, artist, data, duration); // Usa o novo construtor
                songs.add(song);
                Log.d(TAG, "Loaded song: " + title + " by " + artist + ", path: " + data);
            }
            if (!songs.isEmpty()) {
                Executors.newSingleThreadExecutor().execute(() -> {
                    for (Song song : songs) {
                        if (songDao.getSongById(song.getId()) == null) {
                            songDao.insert(song);
                        }
                    }
                    Log.d(TAG, "Inserted/updated " + songs.size() + " songs into database");
                });
            } else {
                Log.w(TAG, "No songs found in MediaStore");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading songs from MediaStore: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Adiciona uma música a uma playlist de forma assíncrona.
     * @param playlistId ID da playlist.
     * @param songId ID da música.
     */
    public void addSongToPlaylist(long playlistId, long songId) {
        Executors.newSingleThreadExecutor().execute(() -> {
            PlaylistSong playlistSong = new PlaylistSong(playlistId, songId);
            playlistDao.insertPlaylistSong(playlistSong);
            Log.d(TAG, "Added song " + songId + " to playlist " + playlistId);
        });
    }

    /**
     * Remove uma música de uma playlist de forma assíncrona.
     * @param playlistId ID da playlist.
     * @param songId ID da música.
     */
    public void removeSongFromPlaylist(long playlistId, long songId) {
        Executors.newSingleThreadExecutor().execute(() -> {
            playlistDao.deletePlaylistSong(playlistId, songId);
            Log.d(TAG, "Removed song " + songId + " from playlist " + playlistId);
        });
    }

    /**
     * Retorna músicas favoritas como LiveData.
     * @return LiveData com lista de músicas favoritas.
     */
    public LiveData<List<Song>> getFavorites() {
        return songDao.getFavorites();
    }

    /**
     * Retorna artistas únicos como LiveData.
     * @return LiveData com lista de artistas.
     */
    public LiveData<List<String>> getAllArtists() {
        return songDao.getAllArtists();
    }

    /**
     * Retorna músicas de um artista específico como LiveData.
     * @param artist Nome do artista.
     * @return LiveData com lista de músicas do artista.
     */
    public LiveData<List<Song>> getSongsByArtist(String artist) {
        return songDao.getSongsByArtist(artist);
    }

    /**
     * Toggle favorito de uma música e atualiza no banco.
     * @param song Música a atualizar.
     */
    public void toggleFavorite(Song song) {
        song.setFavorite(!song.isFavorite()); // Assumindo que Song tem isFavorite e setFavorite
        Executors.newSingleThreadExecutor().execute(() -> songDao.update(song));
        Log.d(TAG, "Toggled favorite for song: " + song.getTitle() + " to " + song.isFavorite());
    }

    /**
     * Cria uma nova playlist.
     * @param name Nome da playlist.
     */
    public void createPlaylist(String name) {
        Executors.newSingleThreadExecutor().execute(() -> {
            Playlist playlist = new Playlist(0, name); // 0 para autoGenerate
            playlistDao.insert(playlist);
            Log.d(TAG, "Created playlist: " + name);
        });
    }
}