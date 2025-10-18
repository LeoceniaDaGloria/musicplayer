package com.example.musicplayer.model;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.Log;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "songs")
public class Song {
    @PrimaryKey(autoGenerate = true)
    private long id;

    private String title;
    private String artist;
    private String path;
    private int duration;

    // ADICIONE ESTE CAMPO
    private long size; // Tamanho do arquivo em bytes

    @ColumnInfo(name = "isFavorite")
    private boolean isFavorite = false;

    @ColumnInfo(name = "isDownloaded")
    private boolean isDownloaded = false;

    private String albumArtUri;
    private String album;

    // NOVOS CAMPOS PARA RAW
    @ColumnInfo(name = "isFromRaw")
    private boolean isFromRaw = false;

    @ColumnInfo(name = "rawResourceId")
    private int rawResourceId = -1;

    // Construtor principal COM size
    public Song(String title, String artist, String path, int duration, long size) {
        this.title = title;
        this.artist = artist;
        this.path = path;
        this.duration = duration;
        this.size = size;
        this.isDownloaded = checkIfDownloaded(path);
        this.albumArtUri = null;
        this.album = "";
        this.isFromRaw = path != null && path.startsWith("raw://");
    }

    @Ignore
    public Song(String title, String artist, String path) {
        this(title, artist, path, 0, 0);
    }

    @Ignore
    public Song(long id, String title, String artist, String path, int duration) {
        this(id, title, artist, path, duration, 0);
    }

    // NOVO CONSTRUTOR COM size
    @Ignore
    public Song(long id, String title, String artist, String path, int duration, long size) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.path = path;
        this.duration = duration;
        this.size = size;
        this.isDownloaded = checkIfDownloaded(path);
        this.albumArtUri = null;
        this.album = "";
        this.isFromRaw = path != null && path.startsWith("raw://");
    }

    // CONSTRUTOR ESPECÍFICO PARA RAW
    @Ignore
    public Song(long id, String title, String artist, String path, int duration, long size, int rawResourceId) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.path = path;
        this.duration = duration;
        this.size = size;
        this.rawResourceId = rawResourceId;
        this.isFromRaw = true;
        this.isDownloaded = true; // Músicas do raw são consideradas "baixadas"
        this.albumArtUri = null;
        this.album = "";
    }

    private boolean checkIfDownloaded(String path) {
        if (path == null) return false;

        String lowerPath = path.toLowerCase();
        return lowerPath.contains("/download/") ||
                lowerPath.contains("/music/") ||
                lowerPath.contains("/dcim/") ||
                lowerPath.contains("/audio/") ||
                lowerPath.startsWith("/storage/emulated/0/") ||
                lowerPath.startsWith("file:///storage/") ||
                lowerPath.contains("/android/media/") ||
                !lowerPath.startsWith("content://") ||
                lowerPath.startsWith("raw://"); // Músicas raw são consideradas baixadas
    }

    @Ignore
    public Bitmap extractAlbumArt(Context context) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            if (isFromRaw && rawResourceId != -1) {
                // Para músicas da pasta raw
                try {
                    android.content.res.AssetFileDescriptor afd = context.getResources().openRawResourceFd(rawResourceId);
                    retriever.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                    afd.close();
                } catch (Resources.NotFoundException e) {
                    Log.e("Song", "Raw resource not found: " + rawResourceId);
                    return null;
                }
            } else if (path != null && !path.isEmpty()) {
                if (path.startsWith("content://")) {
                    retriever.setDataSource(context, Uri.parse(path));
                } else {
                    retriever.setDataSource(path);
                }
            }

            byte[] albumArt = retriever.getEmbeddedPicture();
            if (albumArt != null) {
                return BitmapFactory.decodeByteArray(albumArt, 0, albumArt.length);
            }
        } catch (Exception e) {
            Log.e("Song", "Erro ao extrair capa do album para: " + title + " - " + e.getMessage());
        } finally {
            try {
                retriever.release();
            } catch (Exception e) {
                Log.e("Song", "Erro ao liberar MediaMetadataRetriever: " + e.getMessage());
            }
        }
        return null;
    }

    @Ignore
    public String extractAlbumName(Context context) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            if (isFromRaw && rawResourceId != -1) {
                // Para músicas da pasta raw
                try {
                    android.content.res.AssetFileDescriptor afd = context.getResources().openRawResourceFd(rawResourceId);
                    retriever.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                    afd.close();
                } catch (Resources.NotFoundException e) {
                    Log.e("Song", "Raw resource not found: " + rawResourceId);
                    return "";
                }
            } else if (path != null && !path.isEmpty()) {
                if (path.startsWith("content://")) {
                    retriever.setDataSource(context, Uri.parse(path));
                } else {
                    retriever.setDataSource(path);
                }
            }

            String albumName = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
            return albumName != null ? albumName : "";
        } catch (Exception e) {
            Log.e("Song", "Erro ao extrair nome do album para: " + title + " - " + e.getMessage());
        } finally {
            try {
                retriever.release();
            } catch (Exception e) {
                Log.e("Song", "Erro ao liberar MediaMetadataRetriever: " + e.getMessage());
            }
        }
        return "";
    }

    @Ignore
    public void extractAllMetadata(Context context) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            if (isFromRaw && rawResourceId != -1) {
                // Para músicas da pasta raw
                try {
                    android.content.res.AssetFileDescriptor afd = context.getResources().openRawResourceFd(rawResourceId);
                    retriever.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                    afd.close();
                } catch (Resources.NotFoundException e) {
                    Log.e("Song", "Raw resource not found: " + rawResourceId);
                    return;
                }
            } else if (path != null && !path.isEmpty()) {
                if (path.startsWith("content://")) {
                    retriever.setDataSource(context, Uri.parse(path));
                } else {
                    retriever.setDataSource(path);
                }
            }

            // Extrai álbum
            String albumName = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
            if (albumName != null && !albumName.isEmpty()) {
                this.album = albumName;
            }

            // Extrai duração se não tiver
            if (this.duration == 0) {
                String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (durationStr != null) {
                    try {
                        this.duration = Integer.parseInt(durationStr);
                    } catch (NumberFormatException e) {
                        Log.e("Song", "Erro ao converter duração: " + durationStr);
                    }
                }
            }

            Log.d("Song", "Metadados extraidos para: " + title + " - Album: " + albumName + " - Duração: " + duration);
        } catch (Exception e) {
            Log.e("Song", "Erro ao extrair metadados para: " + title + " - " + e.getMessage());
        } finally {
            try {
                retriever.release();
            } catch (Exception e) {
                Log.e("Song", "Erro ao liberar MediaMetadataRetriever: " + e.getMessage());
            }
        }
    }

    // Getters e Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getTitle() {
        return title != null ? title : "Sem titulo";
    }
    public void setTitle(String title) { this.title = title; }

    public String getArtist() {
        return artist != null ? artist : "Desconhecido";
    }
    public void setArtist(String artist) { this.artist = artist; }

    public String getPath() { return path; }
    public void setPath(String path) {
        this.path = path;
        this.isDownloaded = checkIfDownloaded(path);
        this.isFromRaw = path != null && path.startsWith("raw://");
    }

    public Uri getUri() {
        return path != null ? Uri.parse(path) : null;
    }

    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = duration; }

    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }

    public boolean isFavorite() { return isFavorite; }
    public void setFavorite(boolean favorite) { isFavorite = favorite; }

    public boolean isDownloaded() { return isDownloaded; }
    public void setDownloaded(boolean downloaded) { isDownloaded = downloaded; }

    public String getAlbumArtUri() { return albumArtUri; }
    public void setAlbumArtUri(String albumArtUri) { this.albumArtUri = albumArtUri; }

    public String getAlbum() { return album; }
    public void setAlbum(String album) { this.album = album; }

    // NOVOS GETTERS E SETTERS PARA RAW
    public boolean isFromRaw() { return isFromRaw; }
    public void setFromRaw(boolean fromRaw) { isFromRaw = fromRaw; }

    public int getRawResourceId() { return rawResourceId; }
    public void setRawResourceId(int rawResourceId) { this.rawResourceId = rawResourceId; }
}