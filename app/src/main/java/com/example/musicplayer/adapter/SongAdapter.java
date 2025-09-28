package com.example.musicplayer.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.musicplayer.R;
import com.example.musicplayer.model.Song;
import com.example.musicplayer.presenter.MusicPresenter;

import java.util.List;

/**
 * Adapter para exibir listas de músicas em um RecyclerView.
 * Cada item mostra título, artista e lida com cliques para reprodução e favoritos.
 */
public class SongAdapter extends RecyclerView.Adapter<SongAdapter.ViewHolder> {
    private List<Song> songs; // Lista de músicas a ser exibida
    private OnSongClickListener songListener; // Listener para cliques em músicas
    private MusicPresenter presenter; // Presenter para toggle favorito

    /**
     * Construtor do adapter.
     * @param songs Lista inicial de músicas.
     * @param songListener Interface para lidar com cliques em músicas.
     * @param presenter Presenter para gerenciar ações.
     */
    public SongAdapter(List<Song> songs, OnSongClickListener songListener, MusicPresenter presenter) {
        this.songs = songs;
        this.songListener = songListener;
        this.presenter = presenter;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Infla o layout do item da música
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_musica, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        // Vincula os dados da música ao ViewHolder
        Song song = songs.get(position);
        holder.title.setText(song.getTitle());
        holder.artist.setText(song.getArtist());
        holder.favoriteStar.setImageResource(song.isFavorite() ? R.drawable.ic_star : R.drawable.ic_star_outline);
        holder.favoriteStar.setOnClickListener(v -> {
            // Toggle favorito
            presenter.toggleFavorite(song);
            holder.favoriteStar.setImageResource(song.isFavorite() ? R.drawable.ic_star : R.drawable.ic_star_outline);
        });
        holder.itemView.setOnClickListener(v -> songListener.onSongClick(song));
    }

    @Override
    public int getItemCount() {
        // Retorna o número de músicas na lista
        return songs.size();
    }

    /**
     * ViewHolder para otimizar a exibição de itens na lista.
     */
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView title, artist; // TextViews para título e artista
        ImageView favoriteStar; // ImageView para a estrela de favorito

        ViewHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.song_title);
            artist = itemView.findViewById(R.id.song_artist);
            favoriteStar = itemView.findViewById(R.id.favorite_star);
        }
    }

    /**
     * Interface para callback de cliques em músicas.
     */
    public interface OnSongClickListener {
        void onSongClick(Song song); // Método chamado ao clicar em uma música
    }
}