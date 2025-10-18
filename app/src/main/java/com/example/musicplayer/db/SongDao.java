package com.example.musicplayer.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.musicplayer.model.Song;

import java.util.List;

@Dao
public interface SongDao {
    @Insert
    long insert(Song song);

    @Query("SELECT * FROM songs")
    LiveData<List<Song>> getAll();

    // ✅ CORREÇÃO: Método síncrono adicionado
    @Query("SELECT * FROM songs")
    List<Song> getAllSongsSync();

    @Query("SELECT COUNT(*) FROM songs")
    int getCount();

    @Query("SELECT * FROM songs WHERE path = :path LIMIT 1")
    Song getSongByPath(String path);

    @Query("SELECT * FROM songs WHERE id = :id LIMIT 1")
    Song getSongById(long id);

    @Query("SELECT * FROM songs WHERE isFavorite = 1")
    LiveData<List<Song>> getFavorites();

    @Query("SELECT * FROM songs WHERE isDownloaded = 1")
    LiveData<List<Song>> getDownloadedSongs();

    @Query("SELECT * FROM songs WHERE isFromRaw = 1")
    LiveData<List<Song>> getRawSongs();

    @Query("SELECT DISTINCT artist FROM songs")
    LiveData<List<String>> getAllArtists();

    @Query("SELECT * FROM songs WHERE artist = :artist")
    LiveData<List<Song>> getSongsByArtist(String artist);

    @Update
    void update(Song song);

    @Query("DELETE FROM songs")
    void deleteAll();

    @Delete
    void delete(Song song);

    @Query("SELECT * FROM songs WHERE title LIKE :query OR artist LIKE :query")
    LiveData<List<Song>> searchSongs(String query);

    @Query("SELECT * FROM songs WHERE album LIKE :album")
    LiveData<List<Song>> getSongsByAlbum(String album);

    @Query("SELECT DISTINCT album FROM songs WHERE album IS NOT NULL AND album != ''")
    LiveData<List<String>> getAllAlbums();

    @Query("SELECT * FROM songs ORDER BY id DESC LIMIT :limit")
    LiveData<List<Song>> getRecentSongs(int limit);

    @Query("SELECT * FROM songs ORDER BY id DESC LIMIT :limit")
    LiveData<List<Song>> getMostPlayedSongs(int limit);

    @Query("UPDATE songs SET albumArtUri = :albumArtUri WHERE id = :songId")
    void updateAlbumArt(long songId, String albumArtUri);

    @Query("UPDATE songs SET albumArtUri = NULL WHERE id = :songId")
    void removeAlbumArt(long songId);

    @Query("SELECT * FROM songs WHERE albumArtUri IS NOT NULL")
    LiveData<List<Song>> getSongsWithCustomArt();

    @Query("UPDATE songs SET albumArtUri = NULL")
    void clearAllAlbumArt();

    @Query("SELECT * FROM songs")
    List<Song> getAllSync();
}