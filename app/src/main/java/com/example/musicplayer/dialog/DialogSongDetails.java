// DialogSongDetails.java
package com.example.musicplayer.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.musicplayer.R;
import com.example.musicplayer.model.Song;
import com.example.musicplayer.utils.FileUtils;

import java.util.concurrent.TimeUnit;

public class DialogSongDetails extends Dialog {
    private final Context context;
    private final Song song;

    public DialogSongDetails(@NonNull Context context, Song song) {
        super(context, R.style.MyCustomDialog); // ✅ APLICA O TEMA
        this.context = context;
        this.song = song;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_song_details);

        // ✅ CONFIGURAÇÕES DE TAMANHO
        getWindow().setLayout(
                (int) (getScreenWidth() * 0.90), // 90% da largura (mais informações)
                android.view.WindowManager.LayoutParams.WRAP_CONTENT
        );

        setTitle("Detalhes da Música");
        setCancelable(true);

        initializeViews();
        populateSongDetails();
    }

    private int getScreenWidth() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        ((Activity) context).getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        return displayMetrics.widthPixels;
    }

    private void initializeViews() {
        Button btnClose = findViewById(R.id.btn_close);
        btnClose.setOnClickListener(v -> dismiss());
    }

    private void populateSongDetails() {
        TextView tvTitle = findViewById(R.id.tv_title);
        TextView tvArtist = findViewById(R.id.tv_artist);
        TextView tvAlbum = findViewById(R.id.tv_album);
        TextView tvDuration = findViewById(R.id.tv_duration);
        TextView tvSize = findViewById(R.id.tv_size);
        TextView tvPath = findViewById(R.id.tv_path);
        TextView tvFormat = findViewById(R.id.tv_format);

        if (song != null) {
            tvTitle.setText(song.getTitle());
            tvArtist.setText(song.getArtist() != null ? song.getArtist() : "Artista Desconhecido");
            tvAlbum.setText(song.getAlbum() != null ? song.getAlbum() : "Álbum Desconhecido");
            tvDuration.setText(formatDuration(song.getDuration()));
            tvSize.setText(FileUtils.formatFileSize(song.getSize()));
            tvPath.setText(song.getPath());
            tvFormat.setText(getFileFormat(song.getPath()));
        }
    }

    private String formatDuration(long duration) {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(duration);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(duration) -
                TimeUnit.MINUTES.toSeconds(minutes);
        return String.format("%02d:%02d", minutes, seconds);
    }

    private String getFileFormat(String path) {
        if (path != null && path.contains(".")) {
            return path.substring(path.lastIndexOf(".") + 1).toUpperCase();
        }
        return "Desconhecido";
    }
}