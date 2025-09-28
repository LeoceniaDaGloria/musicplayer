package com.example.musicplayer.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.musicplayer.R;

import java.util.List;

/**
 * Adapter para listar artistas.
 */
public class ArtistsAdapter extends RecyclerView.Adapter<ArtistsAdapter.ViewHolder> {
    private List<String> artists; // Lista de artistas
    private OnArtistClickListener listener; // Listener para cliques em artistas

    /**
     * Construtor do adapter.
     * @param artists Lista de artistas.
     * @param listener Interface para lidar com cliques.
     */
    public ArtistsAdapter(List<String> artists, OnArtistClickListener listener) {
        this.artists = artists;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Infla o layout do item do artista
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.artists_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        // Vincula os dados do artista ao ViewHolder
        String artist = artists.get(position);
        holder.name.setText(artist);
        holder.itemView.setOnClickListener(v -> listener.onArtistClick(artist));
    }

    @Override
    public int getItemCount() {
        // Retorna o número de artistas na lista
        return artists.size();
    }

    /**
     * ViewHolder para otimizar a exibição de itens na lista de artistas.
     */
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name; // TextView para o nome do artista

        ViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.artist_name);
        }
    }

    /**
     * Interface para callback de cliques em artistas.
     */
    public interface OnArtistClickListener {
        void onArtistClick(String artist); // Método chamado ao clicar em um artista
    }
}