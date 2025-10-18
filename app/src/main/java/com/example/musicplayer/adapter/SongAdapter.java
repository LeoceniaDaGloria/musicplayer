package com.example.musicplayer.adapter;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.example.musicplayer.R;
import com.example.musicplayer.dialog.DialogChangeCover;
import com.example.musicplayer.dialog.DialogDeleteSong;
import com.example.musicplayer.dialog.DialogMoveToPlaylist;
import com.example.musicplayer.dialog.DialogRenameSong;
import com.example.musicplayer.dialog.DialogSongDetails;
import com.example.musicplayer.model.Song;
import com.example.musicplayer.presenter.MusicPresenter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.SongViewHolder> {
    private static final String TAG = "SongAdapter";
    private final Context context;
    private List<Song> songs;
    private final OnSongClickListener songClickListener;


    private final OnFavoriteClickListener favoriteClickListener;
    private MusicPresenter presenter;
    private final Executor executor = Executors.newFixedThreadPool(2);

    // VARIAVEL PARA MÚSICA ATUAL
    private Song currentPlayingSong;

    //  INTERFACE CORRIGIDA PARA O MENU DE CONTEXTO
    public interface OnSongMenuListener {
        void onSongDetails(Song song);
        void onAddToPlaylist(Song song);
        void onChangeCover(Song song);
        void onRenameSong(Song song);
        void onDeleteSong(Song song);
    }

    private OnSongMenuListener menuListener;

    // Interface para clique na música
    public interface OnSongClickListener {
        void onSongClick(Song song);
    }

    // Interface separada para clique no favorito
    public interface OnFavoriteClickListener {
        void onFavoriteClick(Song song);
    }

    public SongAdapter(Context context, List<Song> songs,
                       OnSongClickListener songClickListener,
                       OnFavoriteClickListener favoriteClickListener,
                       MusicPresenter presenter) {
        this.context = context;
        this.songs = songs != null ? songs : new ArrayList<>();
        this.songClickListener = songClickListener;
        this.favoriteClickListener = favoriteClickListener;
        this.presenter = presenter;
    }

    public List<Song> getSongs() {
        return songs;
    }

    // Construtor simplificado sem presenter
    public SongAdapter(Context context, List<Song> songs,
                       OnSongClickListener songClickListener,
                       OnFavoriteClickListener favoriteClickListener) {
        this(context, songs, songClickListener, favoriteClickListener, null);
    }

    //  MÉTODO  PARA DEFINIR O LISTENER
    public void setMenuListener(OnSongMenuListener listener) {
        this.menuListener = listener;
    }

    public void setSongs(List<Song> songs) {
        this.songs = songs != null ? songs : new ArrayList<>();
        notifyDataSetChanged();
        Log.d(TAG, "Lista de músicas atualizada: " + this.songs.size() + " músicas");
    }

    // Metodo para atualizar uma música específica
    public void updateSong(Song updatedSong) {
        for (int i = 0; i < songs.size(); i++) {
            if (songs.get(i).getId() == updatedSong.getId()) {
                songs.set(i, updatedSong);
                notifyItemChanged(i);
                Log.d(TAG, "Música atualizada: " + updatedSong.getTitle());
                break;
            }
        }
    }

    // Metodo para atualizar apenas a capa de uma música específica
    public void updateSongCover(Song updatedSong) {
        for (int i = 0; i < songs.size(); i++) {
            if (songs.get(i).getId() == updatedSong.getId()) {
                songs.set(i, updatedSong);
                notifyItemChanged(i);
                Log.d(TAG, "Capa da música atualizada: " + updatedSong.getTitle());
                break;
            }
        }
    }

    // Metodo para remover uma música
    public void removeSong(Song songToRemove) {
        for (int i = 0; i < songs.size(); i++) {
            if (songs.get(i).getId() == songToRemove.getId()) {
                songs.remove(i);
                notifyItemRemoved(i);
                Log.d(TAG, "Música removida: " + songToRemove.getTitle());
                break;
            }
        }
    }

    // Metodo para recarregar completamente
    public void refreshSongList(List<Song> newSongs) {
        this.songs = new ArrayList<>(newSongs);
        notifyDataSetChanged();
        Log.d(TAG, "Lista completamente recarregada: " + newSongs.size() + " músicas");
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.list_item_musica, parent, false);
        return new SongViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        Song song = songs.get(position);
        holder.bind(song);
    }

    @Override
    public int getItemCount() {
        return songs != null ? songs.size() : 0;
    }

    // ============================================================
    //  MTODOS PARA A PLAYLISTDETAILSACTIVITY
    // ============================================================

    /**
     * ATUALIZA A LISTA DE MUSICAS (metodo alternativo para updateSongs) - CORRIGIDO
     */
    public void updateSongs(List<Song> newSongs) {
        Log.d(TAG, "Lista atualizada via updateSongs: " + (newSongs != null ? newSongs.size() : "null") + " músicas");

        // ✅ CORREÇÃO: Usar a lista correta
        if (this.songs == null) {
            this.songs = new ArrayList<>();
        }
        this.songs.clear();
        if (newSongs != null) {
            this.songs.addAll(newSongs);
        }
        notifyDataSetChanged();

        Log.d(TAG, "✅ Lista interna do adapter agora tem: " + this.songs.size() + " músicas");
    }

    /**
     * ✅ DEFINE A MÚSICA ATUALMENTE TOCANDO (sem alterações visuais)
     */
    public void setCurrentPlayingSong(Song currentSong) {
        this.currentPlayingSong = currentSong;
        // ❌ NÃO atualiza a visualização para não alterar cores
        Log.d(TAG, "Música atual definida: " + (currentSong != null ? currentSong.getTitle() : "null"));
    }

    /**
     * ✅ MÉTODO SIMPLIFICADO PARA ATUALIZAR LISTA (alternativa)
     */
    public void refreshSongs(List<Song> newSongs) {
        setSongs(newSongs); // Reutiliza o método existente
    }

    /**
     * ✅ VERIFICA SE A LISTA ESTÁ VAZIA
     */
    public boolean isEmpty() {
        return songs == null || songs.isEmpty();
    }

    /**
     * ✅ OBTÉM O NÚMERO DE MÚSICAS
     */
    public int getSongCount() {
        return songs != null ? songs.size() : 0;
    }

    /**
     * ✅ OBTÉM UMA MÚSICA POR POSIÇÃO
     */
    public Song getSongAt(int position) {
        if (position >= 0 && position < songs.size()) {
            return songs.get(position);
        }
        return null;
    }

    /**
     * ✅ PROCURA UMA MÚSICA PELO ID
     */
    public Song findSongById(long songId) {
        for (Song song : songs) {
            if (song.getId() == songId) {
                return song;
            }
        }
        return null;
    }

    /**
     * ✅ LIMPA A MÚSICA ATUAL (quando para de tocar)
     */
    public void clearCurrentPlayingSong() {
        this.currentPlayingSong = null;
        Log.d(TAG, "Música atual limpa");
    }

    /**
     * ✅ OBTÉM A MÚSICA ATUALMENTE TOCANDO
     */
    public Song getCurrentPlayingSong() {
        return currentPlayingSong;
    }

    // Método para liberar recursos
    public void cleanup() {
        // O executor será fechado quando o adapter for destruído
    }

    // ============================================================
    // ✅ NOVOS MÉTODOS PARA CORES DINÂMICAS
    // ============================================================

    /**
     * ✅ OBTÉM A COR CORRETA BASEADA NO TEMA ATUAL
     */
    private int getColorForTheme(int lightColorRes, int darkColorRes) {
        int nightModeFlags = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;

        if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
            return ContextCompat.getColor(context, darkColorRes);
        } else {
            return ContextCompat.getColor(context, lightColorRes);
        }
    }

    /**
     * ✅ VERIFICA SE É MODO CLARO
     */
    private boolean isLightTheme() {
        int nightModeFlags = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags != Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * ✅ CONFIGURA AS CORES DINÂMICAS PARA OS ELEMENTOS DA LISTA
     */
    private void setupDynamicColors(SongViewHolder holder, Song song) {
        // ✅ TEXTO DO TÍTULO - Preto no claro, Branco no escuro
        int titleColor = getColorForTheme(
                R.color.text_primary_light,
                R.color.text_primary_dark
        );
        holder.songTitle.setTextColor(titleColor);

        // ✅ TEXTO DO ARTISTA - Cinza escuro no claro, Cinza claro no escuro
        int artistColor = getColorForTheme(
                R.color.text_secondary_light,
                R.color.text_secondary_dark
        );
        holder.songArtist.setTextColor(artistColor);

        // ✅ ÍCONE DE FAVORITO - Amarelo em ambos os temas
        int favoriteColor = getColorForTheme(
                R.color.favorite_light,
                R.color.favorite_dark
        );
        holder.favoriteStar.setColorFilter(favoriteColor);

        // ✅ ÍCONE DE MENU (3 PONTOS) - Preto no claro, Branco no escuro
        int menuColor = getColorForTheme(
                R.color.icon_primary_light,
                R.color.icon_primary_dark
        );
        holder.menuButton.setColorFilter(menuColor);

        Log.d(TAG, "✅ Cores dinâmicas aplicadas - Tema: " + (isLightTheme() ? "Claro" : "Escuro"));
    }

    // ✅ NOVO: Método para verificar se uma música está selecionada
    private OnSongSelectionListener selectionListener;

    // ✅ NOVA INTERFACE para seleção
    public interface OnSongSelectionListener {
        boolean isSongSelected(Song song);
        void onSongSelectionChanged(Song song, boolean isSelected);
    }

    //  METODO para definir o listener de seleção
    public void setSelectionListener(OnSongSelectionListener listener) {
        this.selectionListener = listener;
    }

    // CONSTRUTOR para seleção múltipla
    public SongAdapter(Context context, List<Song> songs,
                       OnSongClickListener songClickListener,
                       OnFavoriteClickListener favoriteClickListener,
                       MusicPresenter presenter,
                       OnSongSelectionListener selectionListener) {
        this(context, songs, songClickListener, favoriteClickListener, presenter);
        this.selectionListener = selectionListener;
    }

    class SongViewHolder extends RecyclerView.ViewHolder {
        private final ImageView albumCover;
        private final TextView songTitle;
        private final TextView songArtist;
        private final ImageView favoriteStar;
        private final ImageView menuButton;

        private final CheckBox selectionCheckbox;


        public SongViewHolder(@NonNull View itemView) {
            super(itemView);
            albumCover = itemView.findViewById(R.id.album_cover_list_item);
            songTitle = itemView.findViewById(R.id.song_title);
            songArtist = itemView.findViewById(R.id.song_artist);
            favoriteStar = itemView.findViewById(R.id.favorite_star);
            menuButton = itemView.findViewById(R.id.menu_button);
            selectionCheckbox = itemView.findViewById(R.id.selection_checkbox);

            // Clique normal na música (reproduzir)
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && songClickListener != null) {
                    songClickListener.onSongClick(songs.get(position));
                    Log.d(TAG, "Música clicada para reprodução: " + songs.get(position).getTitle());
                }
            });

            // Clique no favorito
            favoriteStar.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && favoriteClickListener != null) {
                    favoriteClickListener.onFavoriteClick(songs.get(position));
                    Log.d(TAG, "Favorito clicado: " + songs.get(position).getTitle());
                }
            });

            // Clique nos 3 pontinhos (menu de contexto)
            if (menuButton != null) {
                menuButton.setOnClickListener(v -> {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        showSongContextMenu(v, songs.get(position));
                        Log.d(TAG, "Menu de contexto aberto para: " + songs.get(position).getTitle());
                    }
                });
            }

            // ✅ CONFIGURAR VISIBILIDADE do checkbox
            if (selectionCheckbox != null) {
                selectionCheckbox.setVisibility(View.VISIBLE);

                // Clique no checkbox
                selectionCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && selectionListener != null) {
                        selectionListener.onSongSelectionChanged(songs.get(position), isChecked);
                    }
                });
            }

            // Clique normal na música (agora seleciona/deseleciona)
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    if (selectionCheckbox != null && selectionListener != null) {
                        // Alterna seleção
                        Song song = songs.get(position);
                        boolean newState = !selectionListener.isSongSelected(song);
                        selectionCheckbox.setChecked(newState);
                        selectionListener.onSongSelectionChanged(song, newState);
                    } else if (songClickListener != null) {
                        songClickListener.onSongClick(songs.get(position));
                    }
                }
            });
        }

        public void bind(Song song) {
            songTitle.setText(song.getTitle());
            songArtist.setText(song.getArtist());

            // ✅ APLICA CORES DINÂMICAS BASEADAS NO TEMA
            setupDynamicColors(this, song);

            // Carrega capa do álbum - com prioridade para capa personalizada
            loadAlbumArt(song);

            // Atualizar ícone de favorito
            updateFavoriteIcon(song.isFavorite());
        }

        /**
         * Carrega capa do álbum com prioridade:
         * 1. Capa personalizada
         * 2. Capa do arquivo de música
         * 3. Placeholder
         */
        private void loadAlbumArt(Song song) {
            // Configurações do Glide
            RequestOptions requestOptions = new RequestOptions()
                    .transform(new CenterCrop(), new RoundedCorners(16))
                    .placeholder(R.drawable.ic_lm_capa_placeholder)
                    .error(R.drawable.ic_lm_capa_placeholder);

            // PRIMEIRO: Verificar se tem capa personalizada
            if (presenter != null) {
                String customCoverPath = presenter.getSongCoverPath(song.getId());
                if (customCoverPath != null && !customCoverPath.isEmpty()) {
                    File customCoverFile = new File(customCoverPath);
                    if (customCoverFile.exists()) {
                        Log.d(TAG, "Carregando capa personalizada para: " + song.getTitle());
                        Glide.with(context)
                                .load(customCoverFile)
                                .apply(requestOptions)
                                .into(albumCover);
                        return; // Sai do método se carregou capa personalizada
                    }
                }
            }

            // SEGUNDO: Se não tem capa personalizada, mostrar placeholder e extrair do arquivo
            Glide.with(context)
                    .load(R.drawable.ic_lm_capa_placeholder)
                    .apply(requestOptions)
                    .into(albumCover);

            // Extrai capa real em background
            extractAndLoadRealAlbumArt(song, requestOptions);
        }

        /**
         * Extrai e carrega capa real em background thread
         */
        private void extractAndLoadRealAlbumArt(Song song, RequestOptions requestOptions) {
            executor.execute(() -> {
                try {
                    Bitmap albumArt = null;
                    boolean hasError = false;

                    // Tenta extrair capa do arquivo de música
                    if (song.getPath() != null && !song.getPath().startsWith("raw://")) {
                        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                        try {
                            retriever.setDataSource(song.getPath());
                            byte[] albumArtData = retriever.getEmbeddedPicture();
                            if (albumArtData != null) {
                                albumArt = BitmapFactory.decodeByteArray(albumArtData, 0, albumArtData.length);
                                Log.d(TAG, "Capa extraída do arquivo: " + song.getTitle());
                            } else {
                                Log.w(TAG, "Arquivo não tem capa embutida: " + song.getTitle());
                                hasError = true;
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Erro ao extrair capa de " + song.getTitle() + ": " + e.getMessage());
                            hasError = true;
                        } finally {
                            retriever.release();
                        }
                    }

                    final Bitmap finalAlbumArt = albumArt;
                    final boolean finalHasError = hasError;

                    ((android.app.Activity) context).runOnUiThread(() -> {
                        if (finalAlbumArt != null) {
                            Glide.with(context)
                                    .load(finalAlbumArt)
                                    .apply(requestOptions)
                                    .into(albumCover);
                        } else if (finalHasError) {
                            // Mostra ícone especial para arquivos sem capa
                            Glide.with(context)
                                    .load(R.drawable.ic_lm_capa_placeholder)
                                    .apply(requestOptions)
                                    .into(albumCover);
                            Log.d(TAG, "Arquivo sem capa: " + song.getTitle());
                        } else {
                            // Placeholder normal
                            Glide.with(context)
                                    .load(R.drawable.ic_lm_capa_placeholder)
                                    .apply(requestOptions)
                                    .into(albumCover);
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Erro geral ao extrair capa: " + e.getMessage());
                }
            });
        }

        private void updateFavoriteIcon(boolean isFavorite) {
            if (isFavorite) {
                favoriteStar.setImageResource(R.drawable.ic_star);
            } else {
                favoriteStar.setImageResource(R.drawable.ic_star_outline);
            }
        }

        // Mostrar menu de contexto
        private void showSongContextMenu(View view, Song song) {
            PopupMenu popupMenu = new PopupMenu(context, view);
            popupMenu.inflate(R.menu.song_context_menu);

            popupMenu.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();

                if (id == R.id.action_song_details) {
                    // ✅ USA O NOVO LISTENER SE DISPONÍVEL, SENÃO USA O MÉTODO ANTIGO
                    if (menuListener != null) {
                        menuListener.onSongDetails(song);
                    } else {
                        showSongDetails(song);
                    }
                    return true;
                } else if (id == R.id.action_move_to) {
                    // ✅ USA O NOVO LISTENER SE DISPONÍVEL, SENÃO USA O MÉTODO ANTIGO
                    if (menuListener != null) {
                        menuListener.onAddToPlaylist(song);
                    } else {
                        showMoveToPlaylistDialog(song);
                    }
                    return true;
                } else if (id == R.id.action_change_cover) {
                    // ✅ USA O NOVO LISTENER SE DISPONÍVEL, SENÃO USA O MÉTODO ANTIGO
                    if (menuListener != null) {
                        menuListener.onChangeCover(song);
                    } else {
                        changeSongCover(song);
                    }
                    return true;
                } else if (id == R.id.action_rename_song) {
                    // ✅ USA O NOVO LISTENER SE DISPONÍVEL, SENÃO USA O MÉTODO ANTIGO
                    if (menuListener != null) {
                        menuListener.onRenameSong(song);
                    } else {
                        renameSong(song);
                    }
                    return true;
                } else if (id == R.id.action_delete_song) {
                    // ✅ USA O NOVO LISTENER SE DISPONÍVEL, SENÃO USA O MÉTODO ANTIGO
                    if (menuListener != null) {
                        menuListener.onDeleteSong(song);
                    } else {
                        deleteSong(song);
                    }
                    return true;
                }
                return false;
            });

            popupMenu.show();
        }

        // Métodos do menu de contexto - CORRIGIDOS PARA ABRIR DIÁLOGOS
        private void showSongDetails(Song song) {
            try {
                DialogSongDetails dialog = new DialogSongDetails(context, song);
                dialog.show();
                Log.d(TAG, "DialogSongDetails aberto para: " + song.getTitle());
            } catch (Exception e) {
                Log.e(TAG, "Erro ao abrir DialogSongDetails: " + e.getMessage());
                Toast.makeText(context, "Erro ao abrir detalhes", Toast.LENGTH_SHORT).show();
            }
        }

        private void showMoveToPlaylistDialog(Song song) {
            try {
                if (presenter != null) {
                    DialogMoveToPlaylist dialog = new DialogMoveToPlaylist(context, presenter, song);
                    dialog.show();
                    Log.d(TAG, "DialogMoveToPlaylist aberto para: " + song.getTitle());
                } else {
                    Toast.makeText(context, "Funcionalidade indisponível", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao abrir DialogMoveToPlaylist: " + e.getMessage());
                Toast.makeText(context, "Erro ao mover para playlist", Toast.LENGTH_SHORT).show();
            }
        }

        /**
         * Abre dialog para alterar capa com callbacks para atualização
         */
        private void changeSongCover(Song song) {
            try {
                if (presenter != null) {
                    DialogChangeCover dialog = new DialogChangeCover(context, presenter, song);

                    // Configurar callbacks para os novos métodos
                    dialog.setOnCoverChangeListener(new DialogChangeCover.OnCoverChangeListener() {
                        @Override
                        public void onCoverChanged() {
                            // Recarregar a capa específica desta música
                            loadAlbumArt(song);
                            Log.d(TAG, "Capa alterada - recarregando visualização para: " + song.getTitle());

                            // Notificar o adapter para atualizar esta posição específica
                            int position = getAdapterPosition();
                            if (position != RecyclerView.NO_POSITION) {
                                notifyItemChanged(position);
                            }
                        }

                        @Override
                        public void onCoverChangeError(String error) {
                            Log.e(TAG, "Erro ao alterar capa: " + error);
                            Toast.makeText(context, "Erro: " + error, Toast.LENGTH_SHORT).show();
                        }
                    });

                    dialog.show();
                    Log.d(TAG, "DialogChangeCover aberto para: " + song.getTitle());
                } else {
                    Toast.makeText(context, "Funcionalidade indisponível", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao abrir DialogChangeCover: " + e.getMessage());
                Toast.makeText(context, "Erro ao alterar capa", Toast.LENGTH_SHORT).show();
            }
        }

        private void renameSong(Song song) {
            try {
                if (presenter != null) {
                    DialogRenameSong dialog = new DialogRenameSong(context, presenter, song);
                    dialog.show();
                    Log.d(TAG, "DialogRenameSong aberto para: " + song.getTitle());
                } else {
                    Toast.makeText(context, "Funcionalidade indisponível", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao abrir DialogRenameSong: " + e.getMessage());
                Toast.makeText(context, "Erro ao renomear música", Toast.LENGTH_SHORT).show();
            }
        }

        private void deleteSong(Song song) {
            try {
                if (presenter != null) {
                    DialogDeleteSong dialog = new DialogDeleteSong(context, presenter, song);
                    dialog.show();
                    Log.d(TAG, "DialogDeleteSong aberto para: " + song.getTitle());
                } else {
                    Toast.makeText(context, "Funcionalidade indisponível", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao abrir DialogDeleteSong: " + e.getMessage());
                Toast.makeText(context, "Erro ao excluir música", Toast.LENGTH_SHORT).show();
            }
        }
    }
}