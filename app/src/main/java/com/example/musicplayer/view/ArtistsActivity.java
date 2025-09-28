package com.example.musicplayer.view;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.musicplayer.R;
import com.example.musicplayer.adapter.ArtistsAdapter;
import com.example.musicplayer.adapter.SongAdapter;
import com.example.musicplayer.model.Playlist;
import com.example.musicplayer.model.Song;
import com.example.musicplayer.presenter.MusicPresenter;

import java.util.List;

/**
 * Activity para listar artistas e suas músicas.
 * Toolbar sempre visível com menu.
 */
public class ArtistsActivity extends AppCompatActivity implements MusicView {
    private static final String TAG = "ArtistsActivity";

    private MusicPresenter presenter;
    private RecyclerView recyclerViewArtists, recyclerViewSongs;
    private Toolbar toolbar;
    private ImageView albumCover; // Capa visível para músicas

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_artists);

        presenter = new MusicPresenter(this, this, this);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle("Artistas");

        albumCover = findViewById(R.id.album_cover);
        recyclerViewArtists = findViewById(R.id.recycler_view_artists);
        recyclerViewSongs = findViewById(R.id.recycler_view_songs);

        recyclerViewArtists.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewSongs.setLayoutManager(new LinearLayoutManager(this));

        loadArtists();
    }

    /**
     * Carrega lista de artistas.
     */
    private void loadArtists() {
        presenter.loadArtists();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Infla o menu com overflow (três pontos)
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Lida com cliques nos itens do menu
        int id = item.getItemId();
        if (id == R.id.action_favoritas) {
            presenter.loadFavorites();
            Toast.makeText(this, "Mostrando Favoritas", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_descarregadas) {
            presenter.navigateToDescarregadas();
            Toast.makeText(this, "Abrindo Descarregadas", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_artistas) {
            // Já está na ArtistsActivity
            return true;
        } else if (id == R.id.action_recognize) {
            recognizeMusic();
            return true;
        } else if (id == R.id.action_create_playlist) {
            createPlaylist();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void updateArtists(List<String> artists) {
        if (artists != null) {
            ArtistsAdapter adapter = new ArtistsAdapter(artists, new ArtistsAdapter.OnArtistClickListener() {
                @Override
                public void onArtistClick(String artist) {
                    Log.d(TAG, "Artist clicked: " + artist);
                    presenter.loadSongsByArtist(artist);
                }
            });
            recyclerViewArtists.setAdapter(adapter);
        }
    }

    @Override
    public void updateSongInfo(String title, String artist) {
        // Atualiza título e artista na UI
    }

    @Override
    public void updatePlayPauseIcon(boolean isPlaying) {
        // Implemente se necessário (pode usar o mesmo da MainActivity)
    }

    @Override
    public void updateProgress(int progress, int duration) {
        // Implemente se necessário
    }

    @Override
    public void updateSongList(List<Song> songs) {
        SongAdapter adapter = new SongAdapter(songs, song -> {
            Log.d(TAG, "Song clicked: " + song.getTitle());
            presenter.playSong(song);
        }, presenter); // Passa o presenter para toggle favorito
        recyclerViewSongs.setAdapter(adapter);
    }

    @Override
    public void updatePlaylistList(List<Playlist> playlists) {
        // Não aplicável aqui
    }

    @Override
    public void showRecognitionResult(String result) {
        // Implemente se necessário
    }

    @Override
    public void requestPermissions() {
        // Implemente se necessário
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
}