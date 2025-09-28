package com.example.musicplayer.view;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.musicplayer.R;
import com.example.musicplayer.adapter.SongAdapter;
import com.example.musicplayer.model.Playlist;
import com.example.musicplayer.model.Song;
import com.example.musicplayer.presenter.MusicPresenter;

import java.util.List;

/**
 * Activity para exibir músicas favoritas.
 */
public class FavoritasActivity extends AppCompatActivity implements MusicView {
    private static final String TAG = "FavoritasActivity";
    private MusicPresenter presenter;
    private RecyclerView recyclerViewSongs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favoritas);

        presenter = new MusicPresenter(this, this, this);

        recyclerViewSongs = findViewById(R.id.recycler_view_songs);
        recyclerViewSongs.setLayoutManager(new LinearLayoutManager(this));

        // Carrega músicas favoritas (lógica a ser implementada no Presenter)
        presenter.loadFavorites();

        TextView emptyText = findViewById(R.id.empty_text);
        if (recyclerViewSongs.getAdapter() == null || recyclerViewSongs.getAdapter().getItemCount() == 0) {
            emptyText.setVisibility(View.VISIBLE);
        } else {
            emptyText.setVisibility(View.GONE);
        }
    }

    @Override
    public void updateSongList(List<Song> songs) {
        SongAdapter adapter = new SongAdapter(songs, song -> {
            Log.d(TAG, "Song clicked: " + song.getTitle());
            presenter.playSong(song);
        }, presenter);
        recyclerViewSongs.setAdapter(adapter);
        Log.d(TAG, "Updated song list with " + songs.size() + " items");

        TextView emptyText = findViewById(R.id.empty_text);
        if (songs.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
        } else {
            emptyText.setVisibility(View.GONE);
        }
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
        presenter.playSong(song);
    }

    @Override
    public void playPause() {
        presenter.playPause();
    }

    @Override
    public void prevSong() {
        presenter.prevSong();
    }

    @Override
    public void nextSong() {
        presenter.nextSong();
    }

    @Override
    public void toggleShuffle() {
        presenter.toggleShuffle();
    }

    @Override
    public void toggleRepeat() {
        presenter.toggleRepeat();
    }

    @Override
    public void seekTo(int progress) {
        presenter.seekTo(progress);
    }

    @Override
    public void searchSongs(String query) {
        presenter.searchSongs(query);
    }

    @Override
    public void createPlaylist() {
        presenter.createPlaylist();
    }

    @Override
    public void recognizeMusic() {
        presenter.recognizeMusic();
    }

    @Override
    public void loadPlaylistSongs(Playlist playlist) {
        presenter.loadPlaylistSongs(playlist);
    }

    @Override
    public void updateArtists(List<String> artists) {
        // Não aplicável para esta Activity, pois foca em músicas favoritas
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (presenter != null) {
            presenter.onDestroy();
        }
    }
}