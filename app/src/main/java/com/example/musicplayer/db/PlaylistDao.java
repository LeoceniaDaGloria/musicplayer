package com.example.musicplayer.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.musicplayer.model.Playlist;
import com.example.musicplayer.model.PlaylistSong;
import com.example.musicplayer.model.Song;

import java.util.List;

@Dao
public interface PlaylistDao {
    @Insert
    long insert(Playlist playlist);

    // ✅ LIVEDATA: Todas as playlists observáveis
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    LiveData<List<Playlist>> getAll();

    @Query("SELECT * FROM playlists")
    List<Playlist> getAllSync();

    @Insert
    void insertPlaylistSong(PlaylistSong playlistSong);

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    void deletePlaylistSong(long playlistId, long songId);

    // ✅ LIVEDATA: Músicas de uma playlist observáveis
    @Query("SELECT s.* FROM songs s JOIN playlist_songs ps ON s.id = ps.songId WHERE ps.playlistId = :playlistId")
    LiveData<List<Song>> getSongsForPlaylist(long playlistId);

    @Query("SELECT s.* FROM songs s JOIN playlist_songs ps ON s.id = ps.songId WHERE ps.playlistId = :playlistId")
    List<Song> getSongsForPlaylistSync(long playlistId);

    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :playlistId")
    int getSongCountForPlaylistSync(long playlistId);

    @Query("DELETE FROM playlist_songs WHERE songId = :songId")
    void removeSongFromAllPlaylists(long songId);

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    void removeAllSongsFromPlaylist(long playlistId);

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    Playlist getPlaylistById(long playlistId);

    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    int isSongInPlaylist(long playlistId, long songId);

    @Delete
    void delete(Playlist playlist);

    // ✅ LIVEDATA: Contagem de músicas observável
    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :playlistId")
    LiveData<Integer> getSongCountForPlaylist(long playlistId);

    // ============================================================
    // ✅ MÉTODOS QUE ESTÃO FALTANDO - ADICIONE ESTES:
    // ============================================================

    /**
     * ✅ FALTA: Atualiza a contagem de músicas na playlist
     */
    @Query("UPDATE playlists SET songCount = :songCount WHERE id = :playlistId")
    void updateSongCount(long playlistId, int songCount);

    /**
     * ✅ FALTA: Busca relação específica entre playlist e música (para debug)
     */
    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    List<PlaylistSong> getPlaylistSongRelationSync(long playlistId, long songId);

    /**
     * ✅ FALTA: Contagem total de playlists
     */
    @Query("SELECT COUNT(*) FROM playlists")
    int getCount();

    /**
     * ✅ FALTA: Busca playlists por nome
     */
    @Query("SELECT * FROM playlists WHERE name LIKE :query")
    LiveData<List<Playlist>> searchPlaylists(String query);

    /**
     * ✅ FALTA: Atualiza uma playlist
     */
    @Update
    void update(Playlist playlist);

    /**
     * ✅ FALTA: Busca playlist por nome exato
     */
    @Query("SELECT * FROM playlists WHERE name = :name LIMIT 1")
    Playlist getPlaylistByName(String name);

    /**
     * ✅ FALTA: Playlists vazias (sem músicas)
     */
    @Query("SELECT p.* FROM playlists p LEFT JOIN playlist_songs ps ON p.id = ps.playlistId WHERE ps.playlistId IS NULL")
    LiveData<List<Playlist>> getEmptyPlaylists();

    /**
     * ✅ FALTA: Playlists com músicas
     */
    @Query("SELECT DISTINCT p.* FROM playlists p JOIN playlist_songs ps ON p.id = ps.playlistId")
    LiveData<List<Playlist>> getPlaylistsWithSongs();
}