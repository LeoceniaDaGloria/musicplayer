package com.example.musicplayer.view;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.example.musicplayer.model.Song;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;



import com.example.musicplayer.R;
import com.example.musicplayer.adapter.PlaylistAdapter;
import com.example.musicplayer.db.MusicRepository;
import com.example.musicplayer.model.Playlist;
import com.example.musicplayer.presenter.PlaylistPresenter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class PlaylistsActivity extends AppCompatActivity implements PlaylistContract.View {
    private PlaylistPresenter presenter;
    private PlaylistAdapter adapter;
    private RecyclerView recyclerView;
    private TextView emptyText;
    private FloatingActionButton fabCreatePlaylist;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlists);

        setupViews();
        setupPresenter();
        loadPlaylists();
    }

    private void setupViews() {
        // Configurar toolbar
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Minhas Playlists");

        // Configurar RecyclerView
        recyclerView = findViewById(R.id.recycler_view_playlists);
        emptyText = findViewById(R.id.empty_text);
        fabCreatePlaylist = findViewById(R.id.fab_create_playlist);

        adapter = new PlaylistAdapter(new ArrayList<>(), this::onPlaylistClick);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Configurar FAB
        fabCreatePlaylist.setOnClickListener(v -> showCreatePlaylistDialog());

        // Configurar clique na toolbar para voltar
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupPresenter() {
        MusicRepository repository = new MusicRepository(this);
        presenter = new PlaylistPresenter(this, repository);
    }

    private void loadPlaylists() {
        presenter.loadAllPlaylists();
    }

    private void onPlaylistClick(Playlist playlist) {
        // Abrir detalhes da playlist
        Intent intent = new Intent(this, PlaylistDetailsActivity.class);
        intent.putExtra("playlist_id", (int) playlist.getId());
        intent.putExtra("playlist_name", playlist.getName());
        startActivity(intent);
    }

    private void showCreatePlaylistDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Nova Playlist");

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_create_playlist, null);
        com.google.android.material.textfield.TextInputEditText etName = dialogView.findViewById(R.id.et_playlist_name);
        android.widget.Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        android.widget.Button btnCreate = dialogView.findViewById(R.id.btn_create);

        builder.setView(dialogView);

        // Criar diálogo
        androidx.appcompat.app.AlertDialog dialog = builder.create();

        // Configurar botões
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnCreate.setOnClickListener(v -> {
            String name = etName.getText() != null ? etName.getText().toString().trim() : "";
            if (name.isEmpty()) {
                etName.setError("Digite um nome para a playlist");
                return;
            }
            presenter.createPlaylist(name, "");
            dialog.dismiss();
        });

        dialog.show();
    }

    // Implementação da interface PlaylistContract.View
    @Override
    public void showPlaylists(List<Playlist> playlists) {
        adapter.setPlaylists(playlists);

        // Mostrar/ocultar empty state
        if (playlists.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyText.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyText.setVisibility(View.GONE);
        }
    }

    @Override
    public void showPlaylistSongs(List<Song> songs) {
        // Implementado na PlaylistDetailsActivity
    }

    @Override
    public void onPlaylistCreated(Playlist playlist) {
        Toast.makeText(this, "Playlist '" + playlist.getName() + "' criada!", Toast.LENGTH_SHORT).show();
        loadPlaylists(); // Recarregar lista
    }

    @Override
    public void onPlaylistDeleted() {
        Toast.makeText(this, "Playlist excluída", Toast.LENGTH_SHORT).show();
        loadPlaylists();
    }

    @Override
    public void onSongAddedToPlaylist() {
        Toast.makeText(this, "Música adicionada à playlist", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onSongRemovedFromPlaylist() {
        Toast.makeText(this, "Música removida da playlist", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showLoading() {
        // Implementar se necessário
    }

    @Override
    public void hideLoading() {
        // Implementar se necessário
    }
}