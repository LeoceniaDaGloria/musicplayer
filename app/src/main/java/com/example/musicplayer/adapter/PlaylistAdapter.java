package com.example.musicplayer.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.musicplayer.R;
import com.example.musicplayer.model.Playlist;

import java.util.List;

/**
 * Adapter para exibir listas de playlists em um RecyclerView.
 * Cada item mostra o nome da playlist e lida com cliques para seleção.
 */
public class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.ViewHolder> {
    private List<Playlist> playlists; // Lista de playlists a ser exibida
    private OnPlaylistClickListener listener; // Listener para cliques em playlists

    /**
     * Construtor do adapter.
     * @param playlists Lista inicial de playlists.
     * @param listener Interface para lidar com cliques em itens.
     */
    public PlaylistAdapter(List<Playlist> playlists, OnPlaylistClickListener listener) {
        this.playlists = playlists;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Infla o layout do item da playlist
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_playlist, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        // Vincula os dados da playlist ao ViewHolder
        Playlist playlist = playlists.get(position);
        holder.name.setText(playlist.getName());
        holder.itemView.setOnClickListener(v -> listener.onPlaylistClick(playlist));
    }

    @Override
    public int getItemCount() {
        // Retorna o número de playlists na lista
        return playlists.size();
    }

    /**
     * ViewHolder para otimizar a exibição de itens na lista.
     */
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name; // TextView para o nome da playlist

        ViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.playlist_name);
        }
    }

    /**
     * Interface para callback de cliques em playlists.
     */
    public interface OnPlaylistClickListener {
        void onPlaylistClick(Playlist playlist); // Método chamado ao clicar em uma playlist
    }
}