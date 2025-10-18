package com.example.musicplayer.view;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.musicplayer.R;
import com.example.musicplayer.adapter.SongSelectionAdapter;
import com.example.musicplayer.db.MusicRepository;
import com.example.musicplayer.model.Playlist;
import com.example.musicplayer.model.Song;
import com.example.musicplayer.presenter.PlaylistPresenter;

import java.util.ArrayList;
import java.util.List;

public class AddToPlaylistActivity extends AppCompatActivity {

    private int playlistId;
    private MusicRepository repository;
    private SongSelectionAdapter adapter;
    private PlaylistPresenter presenter;

    private RecyclerView recyclerView;
    private TextView emptyText;
    private TextView tvSelectedCount;
    private Button btnAddSelected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_to_playlist);

        // ✅ VERIFICAÇÃO DE SEGURANÇA DO ID
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("playlist_id")) {
            playlistId = intent.getIntExtra("playlist_id", -1);

            if (playlistId == -1) {
                Log.e("AddToPlaylistActivity", "ID da playlist inválido: " + playlistId);
                Toast.makeText(this, "Erro: Playlist inválida", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            Log.d("DEBUG_PLAYLIST", "AddToPlaylistActivity recebeu ID válido: " + playlistId);
        } else {
            Log.e("AddToPlaylistActivity", "Intent não contém playlist_id");
            Toast.makeText(this, "Erro: Dados da playlist não encontrados", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupViews();
        setupPresenter();
        loadAllSongs();
    }

    private void setupViews() {
        // ✅ CONFIGURAR TOOLBAR MANUALMENTE
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("Adicionar Músicas");
        toolbar.setNavigationIcon(R.drawable.ic_chevron_right);
        toolbar.setNavigationOnClickListener(v -> finish());

        // ✅ CONFIGURAR VIEWS
        recyclerView = findViewById(R.id.recycler_view_songs);
        emptyText = findViewById(R.id.empty_text);
        tvSelectedCount = findViewById(R.id.tv_selected_count);
        btnAddSelected = findViewById(R.id.btn_add_selected);

        // ✅ CONFIGURAR ADAPTER DE SELEÇÃO
        adapter = new SongSelectionAdapter(this, new ArrayList<>());

        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // ✅ CONFIGURAR LISTENER DE SELEÇÃO
        adapter.setSelectionListener(selectedSongs -> {
            updateSelectedCount(selectedSongs.size());
        });

        btnAddSelected.setOnClickListener(v -> addSelectedSongsToPlaylist());
        updateSelectedCount(0);
    }

    private void setupPresenter() {
        repository = new MusicRepository(this);
        presenter = new PlaylistPresenter(new PlaylistContract.View() {
            @Override
            public void showPlaylists(List<Playlist> playlists) {
                // Não usado nesta activity
            }

            @Override
            public void showPlaylistSongs(List<Song> songs) {
                // Não usado nesta activity
            }

            @Override
            public void onPlaylistCreated(Playlist playlist) {
                // Não usado nesta activity
            }

            @Override
            public void onPlaylistDeleted() {
                // Não usado nesta activity
            }

            @Override
            public void onSongAddedToPlaylist() {
                // ✅ EXECUTAR NA UI THREAD
                runOnUiThread(() -> {
                    Toast.makeText(AddToPlaylistActivity.this,
                            "Músicas adicionadas com sucesso!", Toast.LENGTH_SHORT).show();

                    // ✅ RETORNAR RESULTADO DE SUCESSO
                    Intent resultIntent = new Intent();
                    setResult(RESULT_OK, resultIntent);
                    finish();
                });
            }

            @Override
            public void onSongRemovedFromPlaylist() {
                // Não usado nesta activity
            }

            @Override
            public void showError(String message) {
                // ✅ EXECUTAR NA UI THREAD
                runOnUiThread(() -> {
                    Toast.makeText(AddToPlaylistActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void showLoading() {
                runOnUiThread(() -> {
                    // ✅ MOSTRAR LOADING SE NECESSÁRIO
                    // findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
                });
            }

            @Override
            public void hideLoading() {
                runOnUiThread(() -> {
                    // ✅ OCULTAR LOADING SE NECESSÁRIO
                    // findViewById(R.id.progressBar).setVisibility(View.GONE);
                });
            }
        }, repository);
    }

    private void loadAllSongs() {
        repository.getAllSongs().observe(this, songs -> {
            if (songs != null && !songs.isEmpty()) {
                adapter.setSongs(songs);
                recyclerView.setVisibility(View.VISIBLE);
                emptyText.setVisibility(View.GONE);
                Log.d("AddToPlaylist", "✅ Carregadas " + songs.size() + " músicas");
            } else {
                recyclerView.setVisibility(View.GONE);
                emptyText.setVisibility(View.VISIBLE);
                Log.d("AddToPlaylist", "❌ Nenhuma música encontrada");
            }
        });
    }

    private void updateSelectedCount(int count) {
        tvSelectedCount.setText(count + " músicas selecionadas");
        btnAddSelected.setEnabled(count > 0);
        Log.d("AddToPlaylist", "🎵 Músicas selecionadas: " + count);
    }

    private void addSelectedSongsToPlaylist() {
        List<Song> selectedSongs = adapter.getSelectedSongs();

        if (selectedSongs.isEmpty()) {
            Toast.makeText(this, "Selecione pelo menos uma música", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d("AddToPlaylist", " Adicionando " + selectedSongs.size() + " músicas à playlist " + playlistId);
        presenter.addSongsToPlaylist(playlistId, selectedSongs);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}