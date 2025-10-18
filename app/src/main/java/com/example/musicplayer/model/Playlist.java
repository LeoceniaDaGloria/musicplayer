package com.example.musicplayer.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Ignore; // ✅ ADICIONE ESTE IMPORT

@Entity(tableName = "playlists")
public class Playlist {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private String name;
    private int songCount;
    private long createdAt;

    // ✅ CONSTRUTOR PADRÃO (usado pelo Room) - SEM @Ignore
    public Playlist() {
        this.createdAt = System.currentTimeMillis();
        this.songCount = 0;
    }

    // ✅ CONSTRUTOR COM PARÂMETROS - MARCADO COM @Ignore
    @Ignore
    public Playlist(long id, String name) {
        this.id = id;
        this.name = name;
        this.songCount = 0;
        this.createdAt = System.currentTimeMillis();
    }

    // ✅ CONSTRUTOR COMPLETO - MARCADO COM @Ignore
    @Ignore
    public Playlist(long id, String name, int songCount, long createdAt) {
        this.id = id;
        this.name = name;
        this.songCount = songCount;
        this.createdAt = createdAt;
    }

    // Getters and Setters (TODOS MANTIDOS)
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getSongCount() { return songCount; }
    public void setSongCount(int songCount) { this.songCount = songCount; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}