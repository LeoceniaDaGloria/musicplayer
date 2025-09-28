package com.example.musicplayer.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.musicplayer.model.Song;

import java.util.List;

/**
 * DAO para operações com músicas.
 */
@Dao
public interface SongDao {
    @Insert
    void insert(Song song); // Insere música

    @Query("SELECT * FROM songs")
    LiveData<List<Song>> getAll(); // Todas as músicas

    @Query("SELECT * FROM songs WHERE isFavorite = 1")
    LiveData<List<Song>> getFavorites(); // Músicas favoritas

    @Query("SELECT DISTINCT artist FROM songs")
    LiveData<List<String>> getAllArtists(); // Artistas únicos

    @Query("SELECT * FROM songs WHERE artist = :artist")
    LiveData<List<Song>> getSongsByArtist(String artist); // Músicas por artista

    @Update
    void update(Song song); // Atualiza música (para toggle favorito)

    @Query("DELETE FROM songs")
    void deleteAll(); // Deleta todas

    @Query("SELECT * FROM songs WHERE id = :id LIMIT 1")
    Song getSongById(long id); // Busca música por ID (ajustado para long)
}