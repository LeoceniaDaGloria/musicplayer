package com.example.musicplayer.view;

import android.content.Intent;
import android.content.res.Configuration;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Activity para exibir músicas favoritas - IMPLEMENTAÇÃO MVP
 * View: Responsável apenas pela UI e interações do usuário
 */
public class FavoritasActivity extends AppCompatActivity implements MusicPresenter.OnMusicOperationListener {
    private static final String TAG = "FavoritasActivity";

    // Componentes MVP
    private MusicPresenter presenter;
    private RecyclerView recyclerViewSongs;
    private Toolbar toolbar;
    private SongAdapter adapter;
    private TextView emptyText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favoritas);
        Log.d(TAG, "=== FAVORITAS ACTIVITY onCreate ===");

        // 1. CONFIGURAÇÃO DA VIEW (UI)
        setupToolbar();
        setupRecyclerView();
        setupEmptyState();

        // 2. INICIALIZAÇÃO DO PRESENTER (LÓGICA DE NEGÓCIO)
        initializePresenter();

        // 3. CARREGAR DADOS
        loadFavoritesWithDelay();
    }

    /**
     * 1. CONFIGURAÇÃO DA VIEW - UI COMPONENTS
     */
    private void setupToolbar() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle("Favoritas");
        }

        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void setupRecyclerView() {
        recyclerViewSongs = findViewById(R.id.recycler_view_songs);
        recyclerViewSongs.setLayoutManager(new LinearLayoutManager(this));

        // Adapter vazio inicial - será atualizado pelo Presenter
        adapter = new SongAdapter(
                this,
                new ArrayList<>(),
                this::onSongClicked,        // Click para reproduzir
                this::onFavoriteClicked,    // Click no favorito
                presenter                   // Presenter para operações
        );
        recyclerViewSongs.setAdapter(adapter);
    }

    private void setupEmptyState() {
        emptyText = findViewById(R.id.empty_text);
        emptyText.setText("Nenhuma música favorita encontrada");
    }

    /**
     * 2. INICIALIZAÇÃO DO PRESENTER
     */
    private void initializePresenter() {
        // Presenter recebe a View (this) e o Context
        presenter = new MusicPresenter(new FavoritasMusicView(), this, this);

        // Configurar listener para operações (diálogos)
        presenter.setOperationListener(this);

        Log.d(TAG, "Presenter inicializado com sucesso");
    }

    /**
     * 3. CARREGAR DADOS - DELEGADO PARA O PRESENTER
     */
    private void loadFavoritesWithDelay() {
        recyclerViewSongs.postDelayed(() -> {
            if (presenter != null) {
                presenter.loadFavorites();
                Log.d(TAG, "Solicitando carregamento de favoritas ao Presenter");
            }
        }, 100);
    }

    // ============================================================
    // IMPLEMENTAÇÃO DA VIEW - MÉTODOS DE INTERAÇÃO DO USUÁRIO
    // ============================================================

    /**
     * Clique em uma música - DELEGA PARA O PRESENTER
     */
    private void onSongClicked(Song song) {
        Log.d(TAG, "Música clicada: " + song.getTitle());

        if (presenter != null) {
            // 1. Abrir player de música
            openMusicPlayer(song);
            // 2. Reproduzir música via Presenter
            presenter.playSong(song);
        }
    }

    /**
     * Clique no botão favorito - DELEGA PARA O PRESENTER
     */
    private void onFavoriteClicked(Song song) {
        Log.d(TAG, "Botão favorito clicado: " + song.getTitle());

        if (presenter != null) {
            presenter.toggleFavorite(song);
            // Recarregar lista para refletir mudança
            presenter.loadFavorites();
        }
    }

    /**
     * Abrir tela do player de música
     */
    private void openMusicPlayer(Song song) {
        MusicNavigationHelper.openMusicPlayer(this, song);
    }

    // ============================================================
    // IMPLEMENTAÇÃO DO OnMusicOperationListener - CALLBACKS DAS OPERAÇÕES
    // ============================================================

    @Override
    public void onSongDeleted(Song song) {
        runOnUiThread(() -> {
            Log.d(TAG, "Callback: Música deletada - " + song.getTitle());

            // 1. Recarregar lista via Presenter
            if (presenter != null) {
                presenter.loadFavorites();
            }

            // 2. Atualizar adapter diretamente para remover a música
            if (adapter != null) {
                adapter.removeSong(song);
                Log.d(TAG, "Música removida do adapter: " + song.getTitle());
            }

            // 3. Controlar estado vazio
            updateEmptyState();

            // 4. Feedback para usuário
            Toast.makeText(this, "Música excluída com sucesso", Toast.LENGTH_SHORT).show();

            Log.d(TAG, "UI atualizada após exclusão");
        });
    }

    @Override
    public void onSongRenamed(Song song) {
        runOnUiThread(() -> {
            Log.d(TAG, "Callback: Música renomeada - " + song.getTitle());

            // 1. Atualizar adapter diretamente
            if (adapter != null) {
                adapter.updateSong(song);
                Log.d(TAG, "Adapter atualizado para música renomeada: " + song.getTitle());
            }

            // 2. Feedback para usuário
            Toast.makeText(this, "Música renomeada com sucesso", Toast.LENGTH_SHORT).show();

            Log.d(TAG, "UI atualizada após renomeação");
        });
    }

    @Override
    public void onCoverChanged() {
        runOnUiThread(() -> {
            Log.d(TAG, "Callback: Capa alterada");

            // 1. Atualizar adapter para mostrar nova capa
            if (adapter != null) {
                adapter.notifyDataSetChanged();
                Log.d(TAG, "Adapter notificado para atualizar capas");
            }

            // 2. Feedback para usuário
            Toast.makeText(this, "Capa atualizada com sucesso", Toast.LENGTH_SHORT).show();

            Log.d(TAG, "UI atualizada após alteração de capa");
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

    // ============================================================
    // CLASSE INTERNA MusicView - IMPLEMENTAÇÃO DA INTERFACE VIEW DO MVP
    // ============================================================

    /**
     * Implementação da MusicView para FavoritasActivity
     * Responsável por atualizar a UI baseado nos comandos do Presenter
     */
    private class FavoritasMusicView implements MusicView {

        /**
         * Atualizar lista de músicas na UI - Chamado pelo Presenter
         */
        @Override
        public void updateSongList(List<Song> songs) {
            runOnUiThread(() -> {
                Log.d(TAG, "View: Atualizando lista com " + songs.size() + " músicas");

                // 1. Atualizar adapter
                if (adapter != null) {
                    adapter.setSongs(songs);
                    Log.d(TAG, "Adapter atualizado com " + songs.size() + " músicas");
                }

                // 2. Controlar estado vazio
                updateEmptyState();
            });
        }

        // ============================================================
        // MÉTODOS DA MusicView - IMPLEMENTAÇÃO COMPLETA
        // ============================================================

        @Override
        public void updatePlaylistList(List<Playlist> playlists) {
            // Não utilizado nesta tela (apenas músicas favoritas)
        }

        @Override
        public void updateSongInfo(String title, String artist) {
            // Não utilizado nesta tela (controles no MusicPlayerActivity)
        }

        @Override
        public void updatePlayPauseIcon(boolean isPlaying) {
            // Não utilizado nesta tela (controles no MusicPlayerActivity)
        }

        @Override
        public void updateProgress(int progress, int duration) {
            // Não utilizado nesta tela (seekbar no MusicPlayerActivity)
        }

        @Override
        public void showRecognitionResult(String result) {
            // Não utilizado nesta tela (reconhecimento no ReconhecerMusicaActivity)
        }

        @Override
        public void requestPermissions() {
            // Não utilizado nesta tela (permissões tratadas no MainActivity)
        }

        @Override
        public void playSong(Song song) {
            // Não utilizado (já tratado no onSongClicked)
        }

        @Override
        public void playPause() {
            // Não utilizado nesta tela
        }

        @Override
        public void prevSong() {
            // Não utilizado nesta tela
        }

        @Override
        public void nextSong() {
            // Não utilizado nesta tela
        }

        @Override
        public void toggleShuffle() {
            // Não utilizado nesta tela
        }

        @Override
        public void toggleRepeat() {
            // Não utilizado nesta tela
        }

        @Override
        public void seekTo(int progress) {
            // Não utilizado nesta tela
        }

        @Override
        public void searchSongs(String query) {
            // Não utilizado nesta tela (busca no MainActivity)
        }

        @Override
        public void createPlaylist() {
            // Não utilizado nesta tela
        }

        @Override
        public void recognizeMusic() {
            // Não utilizado nesta tela
        }

        @Override
        public void loadPlaylistSongs(Playlist playlist) {
            // Não utilizado nesta tela (apenas músicas favoritas)
        }

        @Override
        public void updateArtists(List<String> artists) {
            // Não utilizado nesta tela
        }

        @Override
        public void updateAlbumArt(android.graphics.Bitmap albumArt) {
            // Não utilizado nesta tela (capa no MusicPlayerActivity)
        }
    }

    // ============================================================
    // CICLO DE VIDA E NAVEGAÇÃO
    // ============================================================

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume - Recarregando favoritas");

        // Recarregar dados quando a activity voltar ao foreground
        if (presenter != null) {
            recyclerViewSongs.postDelayed(() -> {
                presenter.loadFavorites();
            }, 300);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy - Liberando recursos");

        // Limpar recursos do Presenter
        if (presenter != null) {
            presenter.onDestroy();
        }

        // Limpar recursos do Adapter
        if (adapter != null) {
            adapter.cleanup();
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

            // Aplicar cor a todos os itens do menu com ícones
            for (int i = 0; i < menu.size(); i++) {
                MenuItem item = menu.getItem(i);

                // Aplicar cor ao ícone se existir
                if (item.getIcon() != null) {
                    // Método 1: Usar setColorFilter (funciona bem)
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
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        // Navegação entre telas - DELEGADO PARA O PRESENTER
        if (id == R.id.action_favoritas) {
            // Já está na tela de favoritas
            return true;
        } else if (id == R.id.action_descarregadas) {
            navigateTo(DescarregadasActivity.class);
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

    /**
     * Navegação entre activities
     */
    private void navigateTo(Class<?> activityClass) {
        try {
            if (this.getClass().equals(activityClass)) {
                return; // Já está na activity
            }
            Intent intent = new Intent(this, activityClass);
            startActivity(intent);
            Log.d(TAG, "Navegando para: " + activityClass.getSimpleName());
        } catch (Exception e) {
            Log.e(TAG, "Erro na navegação: " + e.getMessage());
            Toast.makeText(this, "Erro ao abrir tela", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Método público para atualização manual da lista
     */
    public void refreshMusicList() {
        if (presenter != null) {
            presenter.loadFavorites();
            Toast.makeText(this, "Lista atualizada", Toast.LENGTH_SHORT).show();
        }
    }
}