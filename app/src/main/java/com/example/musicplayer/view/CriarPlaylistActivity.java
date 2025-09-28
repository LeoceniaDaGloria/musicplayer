package com.example.musicplayer.view;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.musicplayer.R;
import com.example.musicplayer.model.Playlist;
import com.example.musicplayer.model.Song;
import com.example.musicplayer.presenter.MusicPresenter;

import java.util.List;

/**
 * Activity para criar uma nova playlist.
 */
public class CriarPlaylistActivity extends AppCompatActivity implements MusicView {
    private static final String TAG = "CriarPlaylistActivity";
    private MusicPresenter presenter;
    private EditText playlistNameInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_criar_playlist);

        presenter = new MusicPresenter(this, this, this);

        playlistNameInput = findViewById(R.id.playlist_name_input);
        Button createButton = findViewById(R.id.create_button);

        createButton.setOnClickListener(v -> {
            String name = playlistNameInput.getText().toString().trim();
            if (!name.isEmpty()) {
                presenter.createPlaylistWithName(name); // Método a ser implementado no Presenter
                Toast.makeText(this, "Playlist '" + name + "' criada!", Toast.LENGTH_SHORT).show();
                finish(); // Fecha a Activity após criar
            } else {
                Toast.makeText(this, "Digite um nome para a playlist", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void updateSongList(List<Song> songs) {
        // Não aplicável para esta Activity
    }

    @Override
    public void updatePlaylistList(List<Playlist> playlists) {
        // Não aplicável para esta Activity
    }

    @Override
    public void updateSongInfo(String title, String artist) {
        // Não aplicável para esta Activity
    }

    @Override
    public void updatePlayPauseIcon(boolean isPlaying) {
        // Não aplicável para esta Activity
    }

    @Override
    public void updateProgress(int progress, int duration) {
        // Não aplicável para esta Activity
    }

    @Override
    public void showRecognitionResult(String result) {
        // Não aplicável para esta Activity
    }

    @Override
    public void requestPermissions() {
        // Não aplicável para esta Activity
    }

    @Override
    public void playSong(Song song) {
        // Não aplicável para esta Activity
    }

    @Override
    public void playPause() {
        // Não aplicável para esta Activity
    }

    @Override
    public void prevSong() {
        // Não aplicável para esta Activity
    }

    @Override
    public void nextSong() {
        // Não aplicável para esta Activity
    }

    @Override
    public void toggleShuffle() {
        // Não aplicável para esta Activity
    }

    @Override
    public void toggleRepeat() {
        // Não aplicável para esta Activity
    }

    @Override
    public void seekTo(int progress) {
        // Não aplicável para esta Activity
    }

    @Override
    public void searchSongs(String query) {
        // Não aplicável para esta Activity
    }

    @Override
    public void createPlaylist() {
        // Não aplicável para esta Activity (usada diretamente no botão)
    }

    @Override
    public void recognizeMusic() {
        // Não aplicável para esta Activity
    }

    @Override
    public void loadPlaylistSongs(Playlist playlist) {
        // Não aplicável para esta Activity
    }

    @Override
    public void updateArtists(List<String> artists) {
        // Não aplicável para esta Activity, pois foca em criar playlists
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (presenter != null) {
            presenter.onDestroy();
        }
    }
}