package com.example.musicplayer.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Modelo de dados para uma playlist.
 * Usado no Room como entidade.
 */
@Entity(tableName = "playlists")
public class Playlist {
    @PrimaryKey(autoGenerate = true)
    private long id; // ID único gerado automaticamente

    private String name; // Nome da playlist

    /**
     * Construtor principal.
     * @param id ID da playlist (0 para autoGenerate).
     * @param name Nome da playlist.
     */
    public Playlist(long id, String name) {
        this.id = id;
        this.name = name;
    }

    /**
     * Getter para ID.
     * @return ID da playlist.
     */
    public long getId() {
        return id;
    }

    /**
     * Setter para ID (usado pelo Room após autoGenerate).
     * @param id Novo ID.
     */
    public void setId(long id) {
        this.id = id;
    }

    /**
     * Getter para nome.
     * @return Nome da playlist.
     */
    public String getName() {
        return name;
    }

    /**
     * Setter para nome.
     * @param name Novo nome.
     */
    public void setName(String name) {
        this.name = name;
    }
}