package com.example.musicplayer.utils;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.musicplayer.model.Song;
import com.example.musicplayer.view.MusicPlayerActivity;

public class MusicNavigationHelper {
    private static final String TAG = "MusicNavigationHelper";

    public static void openMusicPlayer(Context context, Song song) {
        try {
            Intent intent = new Intent(context, MusicPlayerActivity.class);
            intent.putExtra("song_path", song.getPath());
            intent.putExtra("song_title", song.getTitle());
            intent.putExtra("song_artist", song.getArtist());
            intent.putExtra("song_duration", song.getDuration());
            context.startActivity(intent);
            Log.d(TAG, "Abrindo MusicPlayerActivity para: " + song.getTitle());
        } catch (Exception e) {
            Log.e(TAG, "Erro ao abrir MusicPlayerActivity: " + e.getMessage());
        }
    }
}