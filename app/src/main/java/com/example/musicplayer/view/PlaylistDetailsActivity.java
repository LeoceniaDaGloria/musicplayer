package com.example.musicplayer.view;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.musicplayer.R;
import com.example.musicplayer.adapter.SongAdapter;
import com.example.musicplayer.db.MusicRepository;
import com.example.musicplayer.model.Playlist;
import com.example.musicplayer.model.Song;
import com.example.musicplayer.presenter.PlaylistPresenter;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PlaylistDetailsActivity extends AppCompatActivity implements
        PlaylistContract.View,
        SongAdapter.OnSongClickListener,
        SongAdapter.OnFavoriteClickListener,
        SongAdapter.OnSongMenuListener {

    private PlaylistPresenter presenter;
    private SongAdapter adapter;
    private int playlistId;
    private String playlistName;

    private RecyclerView recyclerView;
    private TextView emptyText;
    private FloatingActionButton fabAddSongs;
    private Button btnPlayPlaylist;
    private Button btnShufflePlaylist;
    private List<Song> currentPlaylistSongs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist_details);

        // Obter dados da intent
        playlistId = getIntent().getIntExtra("playlist_id", -1);
        playlistName = getIntent().getStringExtra("playlist_name");

        if (playlistId == -1) {
            Toast.makeText(this, "Playlist não encontrada", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupViews();
        setupPresenter();
        loadPlaylistSongs();
    }

    private void setupViews() {
        // Configurar toolbar
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(playlistName != null ? playlistName : "Playlist");

        // Configurar RecyclerView
        recyclerView = findViewById(R.id.recycler_view_songs);
        emptyText = findViewById(R.id.empty_text);
        fabAddSongs = findViewById(R.id.fab_add_songs);
        btnPlayPlaylist = findViewById(R.id.btn_play_playlist);
        btnShufflePlaylist = findViewById(R.id.btn_shuffle_playlist);

        // Construtor correto do SongAdapter
        adapter = new SongAdapter(
                this, // Context
                new ArrayList<>(), // Lista vazia inicial
                this, // OnSongClickListener
                this, // OnFavoriteClickListener
                null // MusicPresenter (pode ser null para playlists)
        );

        // Configurar o menu listener
        adapter.setMenuListener(this);

        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // ✅ CONFIGURAR BOTÕES DE REPRODUÇÃO
        setupPlaybackButtons();

        // Configurar FAB
        fabAddSongs.setOnClickListener(v -> openAddSongsActivity());

        // Configurar botões de controle
        btnPlayPlaylist.setOnClickListener(v -> playPlaylist(false));
        btnShufflePlaylist.setOnClickListener(v -> playPlaylist(true));

        // Configurar navegação
        toolbar.setNavigationOnClickListener(v -> finish());
    }
    private void setupPlaybackButtons() {
        // ✅ BOTÃO REPRODUZIR (ORDEM NORMAL)
        btnPlayPlaylist.setOnClickListener(v -> {
            if (currentPlaylistSongs.isEmpty()) {
                Toast.makeText(this, "Playlist vazia", Toast.LENGTH_SHORT).show();
                return;
            }
            playPlaylist(false); // Ordem normal
        });

        // ✅ BOTÃO ALEATÓRIA (SHUFFLE)
        btnShufflePlaylist.setOnClickListener(v -> {
            if (currentPlaylistSongs.isEmpty()) {
                Toast.makeText(this, "Playlist vazia", Toast.LENGTH_SHORT).show();
                return;
            }
            playPlaylist(true); // Ordem aleatória
        });

        // ✅ INICIALMENTE DESABILITADOS
        btnPlayPlaylist.setEnabled(false);
        btnShufflePlaylist.setEnabled(false);
    }

    private void setupPresenter() {
        MusicRepository repository = new MusicRepository(this);
        presenter = new PlaylistPresenter(this, repository);
    }

    private void loadPlaylistSongs() {
        presenter.loadPlaylistSongs(playlistId);
    }

    //  Implementação do OnSongClickListener
    @Override
    public void onSongClick(Song song) {
        playSong(song, false);
    }

    //  Implementação do OnFavoriteClickListener
    @Override
    public void onFavoriteClick(Song song) {
        // Toggle favorite
        MusicRepository repository = new MusicRepository(this);
        repository.toggleFavorite(song);
        // O adapter vai atualizar automaticamente via LiveData
    }

    // ✅ REPRODUZIR MÚSICA ESPECÍFICA
    private void playSong(Song song, boolean shuffle) {
        if (song == null) return;

        // ✅ ENCONTRAR POSIÇÃO DA MÚSICA NA LISTA
        int songPosition = -1;
        for (int i = 0; i < currentPlaylistSongs.size(); i++) {
            if (currentPlaylistSongs.get(i).getId() == song.getId()) {
                songPosition = i;
                break;
            }
        }

        if (songPosition == -1) {
            Toast.makeText(this, "Música não encontrada na playlist", Toast.LENGTH_SHORT).show();
            return;
        }

        // ✅ PREPARAR LISTA PARA REPRODUÇÃO
        List<Song> playbackSongs;
        if (shuffle) {
            playbackSongs = new ArrayList<>(currentPlaylistSongs);
            Collections.shuffle(playbackSongs);

            // ✅ ENCONTRAR NOVA POSIÇÃO APÓS EMBARALHAR
            int newPosition = 0;
            for (int i = 0; i < playbackSongs.size(); i++) {
                if (playbackSongs.get(i).getId() == song.getId()) {
                    newPosition = i;
                    break;
                }
            }
            songPosition = newPosition;
        } else {
            playbackSongs = new ArrayList<>(currentPlaylistSongs);
        }

        // ✅ INICIAR REPRODUÇÃO
        startPlayback(playbackSongs, songPosition, playlistId);

        String mode = shuffle ? "🔀 Aleatória: " : "🎵 ";
        Toast.makeText(this, mode + song.getTitle(), Toast.LENGTH_SHORT).show();
    }


    // ✅ REPRODUZIR PLAYLIST COMPLETA
    private void playPlaylist(boolean shuffle) {
        List<Song> playbackSongs;

        if (shuffle) {
            playbackSongs = new ArrayList<>(currentPlaylistSongs);
            Collections.shuffle(playbackSongs);
            Toast.makeText(this, "🔀 Reproduzindo aleatoriamente", Toast.LENGTH_SHORT).show();
        } else {
            playbackSongs = new ArrayList<>(currentPlaylistSongs);
            Toast.makeText(this, "▶️ Reproduzindo playlist", Toast.LENGTH_SHORT).show();
        }

        // ✅ INICIAR REPRODUÇÃO DA PRIMEIRA MÚSICA
        startPlayback(playbackSongs, 0, playlistId);
    }

    // ✅ METODO PARA INICIAR REPRODUÇÃO (INTEGRACAO COM O MUSICPLAYER)
    private void startPlayback(List<Song> songs, int startPosition, int playlistId) {
        if (songs == null || songs.isEmpty()) {
            Toast.makeText(this, "Nenhuma música para reproduzir", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d("Playback", "🎵 Iniciando reprodução: " + songs.size() + " músicas, posição: " + startPosition);

        // ✅ EXTRAIR IDs DAS MÚSICAS
        long[] songIds = new long[songs.size()];
        for (int i = 0; i < songs.size(); i++) {
            songIds[i] = songs.get(i).getId();
        }

        // ✅ ABRIR MusicPlayerActivity COM OS IDs
        Intent playerIntent = new Intent(this, MusicPlayerActivity.class);
        playerIntent.putExtra("playlist_id", playlistId);
        playerIntent.putExtra("current_position", startPosition);
        playerIntent.putExtra("song_ids", songIds);
        playerIntent.putExtra("playlist_name", playlistName);

        startActivity(playerIntent);
    }

    private void openAddSongsActivity() {
        //VERIFICAÇÃO DE SEGURANÇA
        if (playlistId == -1) {
            Toast.makeText(this, "Erro: Playlist não encontrada", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d("DEBUG_PLAYLIST", "Abrindo AddToPlaylistActivity com ID: " + playlistId);

        Intent intent = new Intent(this, AddToPlaylistActivity.class);
        intent.putExtra("playlist_id", playlistId);
        startActivityForResult(intent, 100);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK) {
            // Recarregar músicas após adicionar
            loadPlaylistSongs();
        }
    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Cria o menu programaticamente - SEM XML
        MenuItem deleteItem = menu.add(0, 1001, 0, "Excluir");

        // Tenta usar seu ícone, se não existir usa um do Android
        try {
            deleteItem.setIcon(R.drawable.ic_delete);
        } catch (Resources.NotFoundException e) {
            deleteItem.setIcon(android.R.drawable.ic_delete);
        }

        deleteItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1001) {
            deletePlaylist();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void deletePlaylist() {
        // Use MaterialAlertDialogBuilder (recomendado)
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Excluir Playlist")
                .setMessage("Tem certeza que deseja excluir esta playlist?")
                .setPositiveButton("Excluir", (dialog, which) -> presenter.deletePlaylist(playlistId))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // Implementações do OnSongMenuListener
    @Override
    public void onSongDetails(Song song) {
        Toast.makeText(this, "Detalhes: " + song.getTitle(), Toast.LENGTH_SHORT).show();
        // TODO: Abrir diálogo de detalhes
    }

    @Override
    public void onAddToPlaylist(Song song) {
        // Não faz sentido adicionar à playlist estando já na playlist
        Toast.makeText(this, "Música já está nesta playlist", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onChangeCover(Song song) {
        Toast.makeText(this, "Alterar capa: " + song.getTitle(), Toast.LENGTH_SHORT).show();
        // TODO: Implementar alteração de capa
    }

    @Override
    public void onRenameSong(Song song) {
        Toast.makeText(this, "Renomear: " + song.getTitle(), Toast.LENGTH_SHORT).show();
        // TODO: Implementar renomeação
    }

    @Override
    public void onDeleteSong(Song song) {
        // Remover música da playlist atual
        new android.app.AlertDialog.Builder(this)
                .setTitle("Remover da Playlist")
                .setMessage("Remover '" + song.getTitle() + "' desta playlist?")
                .setPositiveButton("Remover", (dialog, which) ->
                        presenter.removeSongFromPlaylist(playlistId, (int) song.getId()))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // Implementação da interface PlaylistContract.View
    @Override
    public void showPlaylists(List<Playlist> playlists) {
        // Não usado aqui
    }

    @Override
    public void showPlaylistSongs(List<Song> songs) {
        // ATUALIZAR LISTA LOCAL E ADAPTER
        currentPlaylistSongs.clear();
        currentPlaylistSongs.addAll(songs);

        adapter.updateSongs(songs);

        // ATUALIZAR ESTADO DOS BOTÕES
        if (songs.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyText.setVisibility(View.VISIBLE);
            btnPlayPlaylist.setEnabled(false);
            btnShufflePlaylist.setEnabled(false);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyText.setVisibility(View.GONE);
            btnPlayPlaylist.setEnabled(true);
            btnShufflePlaylist.setEnabled(true);
        }
    }

    @Override
    public void onPlaylistCreated(Playlist playlist) {
        // Não usado aqui
    }

    @Override
    public void onPlaylistDeleted() {
        Toast.makeText(this, "Playlist excluída", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    public void onSongAddedToPlaylist() {
        // Recarregar após adicionar música
        loadPlaylistSongs();
    }

    @Override
    public void onSongRemovedFromPlaylist() {
        loadPlaylistSongs();
    }

    @Override
    public void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showLoading() {
    }

    @Override
    public void hideLoading() {

    }

}