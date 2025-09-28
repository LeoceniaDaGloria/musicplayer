package com.example.musicplayer.model;

import android.net.Uri;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Modelo de dados para uma música.
 * Usado no Room como entidade.
 */
@Entity(tableName = "songs")
public class Song {
    @PrimaryKey(autoGenerate = true)
    private long id; // ID único gerado automaticamente

    private String title; // Título da música
    private String artist; // Artista da música
    private String path; // Caminho ou URI da música (armazenado como string)
    private int duration; // Duração em milissegundos
    private boolean isFavorite = false; // Flag para indicar se a música é favorita

    /**
     * Construtor principal.
     * @param title Título da música.
     * @param artist Nome do artista.
     * @param path Caminho ou URI da música.
     * @param duration Duração em milissegundos.
     */
    public Song(String title, String artist, String path, int duration) {
        this.title = title;
        this.artist = artist;
        this.path = path;
        this.duration = duration;
    }

    @Ignore
    /**
     * Construtor alternativo sem duração (padrão 0).
     * @param title Título da música.
     * @param artist Nome do artista.
     * @param path Caminho ou URI da música.
     */
    public Song(String title, String artist, String path) {
        this(title, artist, path, 0);
    }

    @Ignore
    /**
     * Construtor com ID explícito para carregar do MediaStore.
     * @param id ID único da música.
     * @param title Título da música.
     * @param artist Nome do artista.
     * @param path Caminho ou URI da música.
     * @param duration Duração em milissegundos.
     */
    public Song(long id, String title, String artist, String path, int duration) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.path = path;
        this.duration = duration;
    }

    /**
     * Getter para ID.
     * @return ID da música.
     */
    public long getId() {
        return id;
    }

    /**
     * Setter para ID.
     * @param id Novo ID.
     */
    public void setId(long id) {
        this.id = id;
    }

    /**
     * Getter para título (com fallback se null).
     * @return Título da música.
     */
    public String getTitle() {
        return title != null ? title : "Sem título";
    }

    /**
     * Setter para título.
     * @param title Novo título.
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Getter para artista (com fallback se null).
     * @return Artista da música.
     */
    public String getArtist() {
        return artist != null ? artist : "Desconhecido";
    }

    /**
     * Setter para artista.
     * @param artist Novo artista.
     */
    public void setArtist(String artist) {
        this.artist = artist;
    }

    /**
     * Getter para path.
     * @return Caminho ou URI como string.
     */
    public String getPath() {
        return path;
    }

    /**
     * Setter para path.
     * @param path Novo caminho ou URI.
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * Getter para URI (facilita playback).
     * @return URI parsed do path, ou null se path inválido.
     */
    public Uri getUri() {
        return path != null ? Uri.parse(path) : null;
    }

    /**
     * Getter para duração.
     * @return Duração em ms.
     */
    public int getDuration() {
        return duration;
    }

    /**
     * Setter para duração.
     * @param duration Nova duração.
     */
    public void setDuration(int duration) {
        this.duration = duration;
    }

    /**
     * Getter para isFavorite.
     * @return True se a música é favorita.
     */
    public boolean isFavorite() {
        return isFavorite;
    }

    /**
     * Setter para isFavorite.
     * @param favorite Novo valor para favorito.
     */
    public void setFavorite(boolean favorite) {
        isFavorite = favorite;
    }
}