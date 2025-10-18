package com.example.musicplayer.dialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.musicplayer.R;
import com.example.musicplayer.adapter.PlaylistAdapter;
import com.example.musicplayer.model.Playlist;
import com.example.musicplayer.model.Song;
import com.example.musicplayer.presenter.MusicPresenter;

import java.util.List;

public class DialogMoveToPlaylist extends Dialog {
    private static final String TAG = "DialogMoveToPlaylist";
    private final Context context;
    private final MusicPresenter presenter;
    private final Song songToMove;
    private RecyclerView recyclerViewPlaylists;
    private TextView emptyText, dialogTitle;
    private Button btnMove, btnCancel;
    private Playlist selectedPlaylist;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    public DialogMoveToPlaylist(@NonNull Context context, MusicPresenter presenter, Song songToMove) {
        super(context);
        this.context = context;
        this.presenter = presenter;
        this.songToMove = songToMove;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_move_to_playlist);

        Log.d(TAG, "=== DIALOG MOVER PARA PLAYLIST CRIADO ===");
        Log.d(TAG, "Música: " + (songToMove != null ? songToMove.getTitle() : "NULL"));

        initializeViews();
        setupListeners();
        loadPlaylists();
    }

    private void initializeViews() {
        recyclerViewPlaylists = findViewById(R.id.recycler_view_playlists);
        emptyText = findViewById(R.id.empty_text);
        btnMove = findViewById(R.id.btn_move);
        btnCancel = findViewById(R.id.btn_cancel);

        // ✅ CONFIGURAR TÍTULO DINÂMICO
        if (dialogTitle != null && songToMove != null) {
            dialogTitle.setText("Adicionar '" + songToMove.getTitle() + "' à playlist");
        }

        recyclerViewPlaylists.setLayoutManager(new LinearLayoutManager(context));

        // Inicialmente desabilitar o botão mover
        btnMove.setEnabled(false);
        btnMove.setAlpha(0.5f);

        Log.d(TAG, "Views inicializadas");
    }

    private void setupListeners() {
        btnCancel.setOnClickListener(v -> {
            Log.d(TAG, "Botão Cancelar pressionado");
            dismiss();
        });

        btnMove.setOnClickListener(v -> {
            if (selectedPlaylist != null) {
                Log.d(TAG, "Botão Mover pressionado - Playlist: " + selectedPlaylist.getName());
                moveSongToPlaylist();
            } else {
                Toast.makeText(context, "Selecione uma playlist", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadPlaylists() {
        Log.d(TAG, "Carregando playlists...");

        presenter.getAllPlaylistsLiveData().observe((androidx.lifecycle.LifecycleOwner) context, new Observer<List<Playlist>>() {
            @Override
            public void onChanged(List<Playlist> playlists) {
                Log.d(TAG, "Playlists recebidas: " + playlists.size());

                if (playlists.isEmpty()) {
                    emptyText.setVisibility(View.VISIBLE);
                    emptyText.setText("Nenhuma playlist criada\nCrie uma playlist primeiro");
                    Log.d(TAG, "Nenhuma playlist disponível");
                } else {
                    emptyText.setVisibility(View.GONE);

                    // ✅ DEBUG: Log detalhado das playlists
                    for (Playlist playlist : playlists) {
                        Log.d(TAG, "📝 Playlist: '" + playlist.getName() +
                                "' | ID: " + playlist.getId() +
                                " | Músicas: " + playlist.getSongCount());
                    }

                    setupPlaylistAdapter(playlists);
                }
            }
        });
    }

    private void setupPlaylistAdapter(List<Playlist> playlists) {
        Log.d(TAG, "Configurando adapter com " + playlists.size() + " playlists");

        PlaylistAdapter adapter = new PlaylistAdapter(playlists, new PlaylistAdapter.OnPlaylistClickListener() {
            @Override
            public void onPlaylistClick(Playlist playlist) {
                // Quando uma playlist é selecionada
                selectedPlaylist = playlist;
                btnMove.setEnabled(true);
                btnMove.setAlpha(1.0f);

                Log.d(TAG, "=== PLAYLIST SELECIONADA ===");
                Log.d(TAG, "Playlist: " + playlist.getName() + " (ID: " + playlist.getId() + ")");
                Log.d(TAG, "Música: " + songToMove.getTitle() + " (ID: " + songToMove.getId() + ")");
                Log.d(TAG, "Total de músicas na playlist: " + playlist.getSongCount());

                // Feedback visual
                Toast.makeText(context, "Selecionado: " + playlist.getName(), Toast.LENGTH_SHORT).show();
            }
        });
        recyclerViewPlaylists.setAdapter(adapter);

        Log.d(TAG, "Adapter configurado com sucesso");
    }

    /**
     * ✅ MÉTODO PRINCIPAL CORRIGIDO: Adiciona música à playlist com verificação robusta
     */
    private void moveSongToPlaylist() {
        if (selectedPlaylist != null && songToMove != null) {
            Log.d(TAG, "=== INICIANDO PROCESSO DE ADIÇÃO ===");
            Log.d(TAG, "Playlist: " + selectedPlaylist.getName() + " (ID: " + selectedPlaylist.getId() + ")");
            Log.d(TAG, "Música: " + songToMove.getTitle() + " (ID: " + songToMove.getId() + ")");

            // ✅ 1. DESABILITAR UI PARA EVITAR MÚLTIPLOS CLICKS
            setUiState(false, "Verificando...");

            // ✅ 2. VERIFICAÇÃO EM DUAS ETAPAS
            verifyAndAddSong();

        } else {
            Log.e(TAG, "❌ Dados inválidos - Playlist: " + selectedPlaylist + ", Música: " + songToMove);
            Toast.makeText(context, "Erro: Dados inválidos", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * ✅ VERIFICAÇÃO ROBUSTA EM DUAS ETAPAS
     */
    private void verifyAndAddSong() {
        Log.d(TAG, "🔍 ETAPA 1: Verificando se música já está na playlist...");

        // ✅ PRIMEIRO: Verificação rápida com getSongsForPlaylist
        presenter.getSongsForPlaylist(selectedPlaylist.getId(), new Observer<List<Song>>() {
            @Override
            public void onChanged(List<Song> existingSongs) {
                Log.d(TAG, "🔍 Verificação rápida - " + existingSongs.size() + " músicas na playlist");

                boolean alreadyExists = false;

                // Verificar por ID (mais confiável)
                for (Song existingSong : existingSongs) {
                    if (existingSong.getId() == songToMove.getId()) {
                        alreadyExists = true;
                        Log.w(TAG, "⚠️ Música já está na playlist (verificação por ID)");
                        break;
                    }
                }

                // ✅ Verificação adicional por título e artista (fallback)
                if (!alreadyExists) {
                    for (Song existingSong : existingSongs) {
                        if (existingSong.getTitle().equals(songToMove.getTitle()) &&
                                existingSong.getArtist().equals(songToMove.getArtist())) {
                            alreadyExists = true;
                            Log.w(TAG, "⚠️ Música já está na playlist (verificação por título/artista)");
                            break;
                        }
                    }
                }

                if (alreadyExists) {
                    // ❌ MÚSICA JÁ EXISTE
                    handleSongAlreadyExists();
                } else {
                    // ✅ MÚSICA NÃO EXISTE - PROCEDER COM ADIÇÃO
                    Log.d(TAG, "✅ Música NÃO encontrada na playlist - procedendo com adição");
                    addSongToPlaylist();
                }
            }
        });
    }

    /**
     * ✅ ADICIONAR MÚSICA À PLAYLIST
     */
    private void addSongToPlaylist() {
        Log.d(TAG, "🎵 ETAPA 2: Adicionando música à playlist...");
        setUiState(false, "Adicionando...");

        presenter.addSongToPlaylist(selectedPlaylist.getId(), songToMove, new MusicPresenter.OnPlaylistOperationListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "✅ ETAPA 3: Música adicionada com SUCESSO!");

                runOnUiThread(() -> {
                    // ✅ FEEDBACK POSITIVO
                    String successMessage = "✅ '" + songToMove.getTitle() + "' adicionada a '" + selectedPlaylist.getName() + "'";
                    Toast.makeText(context, successMessage, Toast.LENGTH_LONG).show();

                    Log.d(TAG, "✅ " + successMessage);

                    // ✅ ATUALIZAR DADOS
                    presenter.loadPlaylists(); // Atualiza lista de playlists
                    presenter.forceServiceSync(); // Atualiza service se necessário

                    Log.d(TAG, "🔄 Dados atualizados com sucesso");

                    // ✅ FECHAR DIALOG COM SUCESSO
                    dismissWithSuccess();
                });
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "❌ ETAPA 3: Erro ao adicionar música: " + error);

                runOnUiThread(() -> {
                    // ✅ ANALISAR TIPO DE ERRO
                    if (error.contains("UNIQUE") || error.contains("duplicate") || error.toLowerCase().contains("já existe")) {
                        // ❌ ERRO DE DUPLICATA (mesmo após verificação)
                        Log.w(TAG, "⚠️ Duplicata detectada pelo banco de dados");
                        handleSongAlreadyExists();
                    } else {
                        // ❌ OUTRO ERRO
                        String errorMessage = "❌ Erro ao adicionar música: " + error;
                        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show();
                        Log.e(TAG, errorMessage);

                        // Reabilitar UI
                        setUiState(true, "Mover");
                    }
                });
            }
        });
    }

    /**
     * ✅ TRATAMENTO QUANDO MÚSICA JÁ EXISTE
     */
    private void handleSongAlreadyExists() {
        Log.w(TAG, "❌ Música já está na playlist - mostrando feedback...");

        runOnUiThread(() -> {
            String message = "❌ '" + songToMove.getTitle() + "' já está em '" + selectedPlaylist.getName() + "'";
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();

            Log.w(TAG, message);

            // ✅ FECHAR DIALOG APÓS FEEDBACK
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                dismiss();
            }, 1500); // Pequeno delay para usuário ver a mensagem
        });
    }

    /**
     * ✅ CONTROLAR ESTADO DA UI
     */
    private void setUiState(boolean enabled, String buttonText) {
        runOnUiThread(() -> {
            btnMove.setEnabled(enabled);
            btnMove.setText(buttonText);
            btnMove.setAlpha(enabled ? 1.0f : 0.5f);

            btnCancel.setEnabled(enabled);

            Log.d(TAG, "UI atualizada - Botão: " + buttonText + ", Habilitado: " + enabled);
        });
    }

    /**
     * ✅ FECHAR DIALOG COM ANIMAÇÃO DE SUCESSO
     */
    private void dismissWithSuccess() {
        runOnUiThread(() -> {
            // Pequena animação visual antes de fechar
            btnMove.animate()
                    .scaleX(1.1f).scaleY(1.1f)
                    .setDuration(200)
                    .withEndAction(() -> {
                        btnMove.animate()
                                .scaleX(1.0f).scaleY(1.0f)
                                .setDuration(200)
                                .withEndAction(() -> dismiss())
                                .start();
                    })
                    .start();
        });
    }

    /**
     * ✅ EXECUTAR NO UI THREAD
     */
    private void runOnUiThread(Runnable action) {
        if (context instanceof android.app.Activity) {
            ((android.app.Activity) context).runOnUiThread(action);
        } else {
            mainHandler.post(action);
        }
    }

    @Override
    public void dismiss() {
        Log.d(TAG, "=== DIALOG FECHADO ===");
        super.dismiss();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "Dialog finalizado");

        // Limpar recursos
        mainHandler.removeCallbacksAndMessages(null);
    }
}