package com.example.musicplayer.db;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import android.content.Context;

import com.example.musicplayer.model.Playlist;
import com.example.musicplayer.model.PlaylistSong;
import com.example.musicplayer.model.Song;

/**
 * Definição do banco de dados Room para o app.
 * Inclui entidades para músicas, playlists e relações entre elas.
 */
@Database(entities = {Song.class, Playlist.class, PlaylistSong.class}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract SongDao songDao();
    public abstract PlaylistDao playlistDao();

    private static volatile AppDatabase INSTANCE;

    // ✅ CORREÇÃO: Metodo getDatabase adicionado
    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "music_db")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}