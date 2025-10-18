package com.example.musicplayer.model;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

@Entity(
        tableName = "playlist_songs",
        primaryKeys = {"playlistId", "songId"},
        foreignKeys = {
                @ForeignKey(
                        entity = Playlist.class,
                        parentColumns = "id",
                        childColumns = "playlistId",
                        onDelete = ForeignKey.CASCADE
                ),
                @ForeignKey(
                        entity = Song.class,
                        parentColumns = "id",
                        childColumns = "songId",
                        onDelete = ForeignKey.CASCADE
                )
        },
        indices = {
                @Index(value = {"playlistId"}),
                @Index(value = {"songId"})
        }
)
public class PlaylistSong {
    private long playlistId;
    private long songId;

    public PlaylistSong(long playlistId, long songId) {
        this.playlistId = playlistId;
        this.songId = songId;
    }

    public long getPlaylistId() { return playlistId; }
    public void setPlaylistId(long playlistId) { this.playlistId = playlistId; }

    public long getSongId() { return songId; }
    public void setSongId(long songId) { this.songId = songId; }
}