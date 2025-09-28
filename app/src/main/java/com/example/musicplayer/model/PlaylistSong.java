package com.example.musicplayer.model;

import androidx.room.Entity;
import androidx.room.ForeignKey;

/**
 * Modelo de dados para a relação entre playlist e música.
 * Usado no Room como entidade.
 */
@Entity(tableName = "playlist_songs",
        primaryKeys = {"playlistId", "songId"},
        foreignKeys = {
                @ForeignKey(entity = Playlist.class, parentColumns = "id", childColumns = "playlistId"),
                @ForeignKey(entity = Song.class, parentColumns = "id", childColumns = "songId")
        })
public class PlaylistSong {
    private final long playlistId; // ID da playlist
    private final long songId; // ID da música

    /**
     * Construtor principal.
     * @param playlistId ID da playlist.
     * @param songId ID da música.
     */
    public PlaylistSong(long playlistId, long songId) {
        this.playlistId = playlistId;
        this.songId = songId;
    }

    /**
     * Getter para playlistId.
     * @return ID da playlist.
     */
    public long getPlaylistId() {
        return playlistId;
    }

    /**
     * Getter para songId.
     * @return ID da música.
     */
    public long getSongId() {
        return songId;
    }
}