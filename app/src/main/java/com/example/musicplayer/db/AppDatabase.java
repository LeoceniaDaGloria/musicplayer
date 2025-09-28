package com.example.musicplayer.db;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.example.musicplayer.model.Playlist;
import com.example.musicplayer.model.PlaylistSong;
import com.example.musicplayer.model.Song;

/**
 * Definição do banco de dados Room para o app.
 * Inclui entidades para músicas, playlists e relações entre elas.
 */
@Database(entities = {Song.class, Playlist.class, PlaylistSong.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    /**
     * Retorna o DAO para operações com músicas.
     * @return Instância do SongDao.
     */
    public abstract SongDao songDao();

    /**
     * Retorna o DAO para operações com playlists.
     * @return Instância do PlaylistDao.
     */
    public abstract PlaylistDao playlistDao();
}