package com.example.musicplayer.view;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.musicplayer.R;
import com.example.musicplayer.adapter.SongAdapter;
import com.example.musicplayer.model.Playlist;
import com.example.musicplayer.model.Song;
import com.example.musicplayer.presenter.MusicPresenter;
import com.example.musicplayer.utils.MusicNavigationHelper;

import java.util.List;

/**
 * Activity para exibir músicas descarregadas.
 */
public class DescarregadasActivity extends AppCompatActivity implements MusicView, MusicPresenter.OnMusicOperationListener {
    private static final String TAG = "DescarregadasActivity";
    private MusicPresenter presenter;
    private RecyclerView recyclerViewSongs;
    private Toolbar toolbar;
    private SongAdapter adapter;
    private TextView emptyText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_descarregadas);

        // Configura a Toolbar
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle("Descarregadas");
        }

        toolbar.setNavigationOnClickListener(v -> {
            onBackPressed();
        });

        presenter = new MusicPresenter(this, this, this);
        presenter.setOperationListener(this); // ✅ ADICIONE ESTA LINHA

        recyclerViewSongs = findViewById(R.id.recycler_view_songs);
        recyclerViewSongs.setLayoutManager(new LinearLayoutManager(this));

        emptyText = findViewById(R.id.empty_text);

        // Carrega músicas descarregadas
        presenter.loadDownloadedSongs();
    }

    // ============================================================
    // IMPLEMENTAÇÃO DO LISTENER PARA OPERAÇÕES DOS DIÁLOGOS
    // ============================================================

    @Override
    public void onSongDeleted(Song song) {
        runOnUiThread(() -> {
            // Atualizar adapter diretamente
            if (adapter != null) {
                adapter.removeSong(song);
            }

            // Verificar estado vazio
            updateEmptyState();

            Toast.makeText(this, "Música excluída", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Música deletada das descarregadas: " + song.getTitle());
        });
    }

    @Override
    public void onSongRenamed(Song song) {
        runOnUiThread(() -> {
            // Atualizar adapter diretamente
            if (adapter != null) {
                adapter.updateSong(song);
            }

            Toast.makeText(this, "Música renomeada", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Música renomeada nas descarregadas: " + song.getTitle());
        });
    }

    @Override
    public void onCoverChanged() {
        runOnUiThread(() -> {
            // Atualizar o adapter para mostrar novas capas
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
            Toast.makeText(this, "Capa atualizada", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Capa atualizada nas descarregadas");
        });
    }

    /**
     * Atualizar estado vazio da lista
     */
    private void updateEmptyState() {
        if (adapter != null) {
            if (adapter.getItemCount() == 0) {
                emptyText.setVisibility(View.VISIBLE);
                Log.d(TAG, "Lista vazia - mostrando estado vazio");
            } else {
                emptyText.setVisibility(View.GONE);
                Log.d(TAG, "Lista com itens - " + adapter.getItemCount() + " músicas");
            }
        }
    }

    /**
     * Obtém cor baseada no tema atual (claro/escuro)
     */
    private int getColorForTheme(@ColorRes int lightColor, @ColorRes int darkColor) {
        int currentNightMode = getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;

        if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
            return ContextCompat.getColor(this, darkColor);
        } else {
            return ContextCompat.getColor(this, lightColor);
        }
    }

    /**
     * Aplica cores dinâmicas aos itens do menu baseado no tema (claro/escuro)
     */
    private void applyDynamicMenuColors(Menu menu) {
        try {
            // Obter a cor dinâmica para ícones
            int menuIconColor = getColorForTheme(
                    R.color.icon_primary_light,
                    R.color.icon_primary_dark
            );

            // Obter cor para texto (opcional)
            int menuTextColor = getColorForTheme(
                    R.color.text_primary_light,
                    R.color.text_primary_dark
            );

            Log.d(TAG, "Aplicando cores dinâmicas ao menu - Cor: " + Integer.toHexString(menuIconColor));

            // Aplicando cor a todos os itens do menu com ícones
            for (int i = 0; i < menu.size(); i++) {
                MenuItem item = menu.getItem(i);

                // Aplicar cor ao ícone se existir
                if (item.getIcon() != null) {
                    // Método 1: Usa setColorFilter (funciona bem)
                    item.getIcon().setColorFilter(menuIconColor, android.graphics.PorterDuff.Mode.SRC_IN);

                    // Método 2: Alternativa usando tint (Android 8.0+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        item.getIcon().setTint(menuIconColor);
                    }

                    Log.d(TAG, "Cor aplicada ao ícone: " + item.getTitle());
                }

                // Opcional: Aplicar cor ao texto do menu (para overflow)
                SpannableString spanString = new SpannableString(item.getTitle());
                spanString.setSpan(new ForegroundColorSpan(menuTextColor), 0, spanString.length(), 0);
                item.setTitle(spanString);
            }

        } catch (Exception e) {
            Log.e(TAG, "Erro ao aplicar cores dinâmicas ao menu: " + e.getMessage());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);

        // CONFIGURAÇÃO DE CORES DINÂMICAS DO MENU
        applyDynamicMenuColors(menu);


        // CONFIGURAÇÃO DO SEARCHVIEW
        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();

        if (searchView != null) {
            // PARA DECLARAR VARIÁVEIS FORA DO BLOCO TRY
            int hintColor;
            int fallbackHintColor = Color.LTGRAY;

            // Para Usar cores dinâmicas do sistema
            try {
                // Usar IDs do AppCompat
                EditText searchEditText = searchView.findViewById(androidx.appcompat.R.id.search_src_text);
                if (searchEditText != null) {
                    // ✅ CORES PARA MODO ESCURO
                    int textColor = Color.WHITE;
                    hintColor = Color.LTGRAY;

                    searchEditText.setTextColor(textColor);
                    searchEditText.setHintTextColor(hintColor);

                    Log.d(TAG, "SearchView configurado com cores para modo escuro");

                    // ✅ CONFIGURAR CORES DOS ÍCONES TAMBÉM
                    ImageView searchIcon = searchView.findViewById(androidx.appcompat.R.id.search_mag_icon);
                    if (searchIcon != null) {
                        searchIcon.setColorFilter(hintColor);
                    }

                    ImageView closeIcon = searchView.findViewById(androidx.appcompat.R.id.search_close_btn);
                    if (closeIcon != null) {
                        closeIcon.setColorFilter(hintColor);
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "Erro ao configurar cor do SearchView: " + e.getMessage());

                // ✅ FALLBACK SIMPLES
                try {
                    EditText searchEditText = searchView.findViewById(androidx.appcompat.R.id.search_src_text);
                    if (searchEditText != null) {
                        // Cores fixas como fallback
                        searchEditText.setTextColor(Color.WHITE);
                        searchEditText.setHintTextColor(fallbackHintColor);

                        // Configurar ícones no fallback também
                        ImageView searchIcon = searchView.findViewById(androidx.appcompat.R.id.search_mag_icon);
                        if (searchIcon != null) {
                            searchIcon.setColorFilter(fallbackHintColor);
                        }

                        ImageView closeIcon = searchView.findViewById(androidx.appcompat.R.id.search_close_btn);
                        if (closeIcon != null) {
                            closeIcon.setColorFilter(fallbackHintColor);
                        }
                    }
                } catch (Exception fallbackEx) {
                    Log.e(TAG, "Erro no fallback do SearchView: " + fallbackEx.getMessage());
                }
            }

            // Configurar hint
            searchView.setQueryHint("Pesquisar músicas...");

            // Listener para pesquisa
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    searchSongs(query);
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    searchSongs(newText);
                    return true;
                }
            });
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_favoritas) {
            navigateTo(FavoritasActivity.class);
            return true;
        } else if (id == R.id.action_descarregadas) {
            // Já está na Descarregadas, não faz nada
            return true;
        } else if (id == R.id.action_artistas) {
            navigateTo(ArtistsActivity.class);
            return true;
        } else if (id == R.id.action_recognize) {
            navigateTo(ReconhecerMusicaActivity.class);
            return true;
        } else if (id == R.id.action_playlists) {
            navigateTo(PlaylistsActivity.class);
            return true;
        } else if (id == android.R.id.home) {
            onBackPressed();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void navigateTo(Class<?> activityClass) {
        try {
            if (this.getClass().equals(activityClass)) {
                return; // Já está na activity
            }
            Intent intent = new Intent(this, activityClass);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao navegar: " + e.getMessage());
        }
    }

    private void openMusicPlayer(Song song) {
        MusicNavigationHelper.openMusicPlayer(this, song);
    }

    // ============================================================
    //  updateAlbumArt - REQUERIDO PELA INTERFACE MusicView
    // ============================================================

    @Override
    public void updateAlbumArt(Bitmap albumArt) {
        // Este método é chamado pelo Presenter para atualizar a capa do álbum
        // Na DescarregadasActivity, não temos um ImageView principal para a capa,
        // então as capas são gerenciadas individualmente pelo SongAdapter
        Log.d(TAG, "updateAlbumArt chamado - Capa recebida: " + (albumArt != null ? "Bitmap válido" : "null"));

        // Se necessário, podemos atualizar alguma UI específica de capa aqui
        // Por exemplo, uma capa principal ou banner da activity
    }

    @Override
    public void updateSongList(List<Song> songs) {
        //  Usando as duas interfaces separadas com lambda
        adapter = new SongAdapter(
                this, // Context
                songs, // List<Song>
                song -> {
                    Log.d(TAG, "Song clicked: " + song.getTitle());
                    openMusicPlayer(song);
                    presenter.playSong(song);
                },
                song -> {
                    Log.d(TAG, "Favorite clicked: " + song.getTitle());
                    presenter.toggleFavorite(song);
                },
                presenter
        );

        recyclerViewSongs.setAdapter(adapter);
        Log.d(TAG, "Updated song list with " + songs.size() + " items");

        updateEmptyState();
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
        // Não aplicável para esta Activity, pois foca em músicas descarregadas
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (presenter != null) {
            presenter.onDestroy();
        }

        // Limpar recursos do adapter
        if (adapter != null) {
            adapter.cleanup();
        }
    }
}