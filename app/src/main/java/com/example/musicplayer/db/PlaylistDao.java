package com.example.musicplayer.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.musicplayer.model.Playlist;
import com.example.musicplayer.model.PlaylistSong;
import com.example.musicplayer.model.Song;

import java.util.List;

/**
 * DAO para operações com playlists.
 */
@Dao
public interface PlaylistDao {
    @Insert
    void insert(Playlist playlist); // Insere playlist

    @Query("SELECT * FROM playlists")
    LiveData<List<Playlist>> getAll(); // Todas as playlists

    @Insert
    void insertPlaylistSong(PlaylistSong playlistSong); // Insere relação playlist-música

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    void deletePlaylistSong(long playlistId, long songId); // Remove relação

    @Query("SELECT s.* FROM songs s JOIN playlist_songs ps ON s.id = ps.songId WHERE ps.playlistId = :playlistId")
    LiveData<List<Song>> getSongsForPlaylist(long playlistId); // Músicas de uma playlist
}