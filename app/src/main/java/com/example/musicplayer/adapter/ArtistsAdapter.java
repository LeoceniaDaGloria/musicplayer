package com.example.musicplayer.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.request.RequestOptions;
import com.example.musicplayer.R;

import java.util.List;

/**
 * Adapter para listar artistas.
 */
public class ArtistsAdapter extends RecyclerView.Adapter<ArtistsAdapter.ViewHolder> {
    private List<String> artists; // Lista de artistas
    private OnArtistClickListener listener; // Listener para cliques em artistas
    private Context context; // Contexto para usar o Glide

    /**
     * Construtor do adapter.
     * @param artists Lista de artistas.
     * @param listener Interface para lidar com cliques.
     */
    public ArtistsAdapter(Context context, List<String> artists, OnArtistClickListener listener) {
        this.context = context;
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

        // Carrega a imagem do artista usando Glide
        loadArtistImage(holder.artistImage, artist);

        holder.itemView.setOnClickListener(v -> listener.onArtistClick(artist));
    }

    @Override
    public int getItemCount() {
        // Retorna o número de artistas na lista
        return artists.size();
    }

    /**
     * Carrega a imagem do artista usando Glide
     */
    private void loadArtistImage(ImageView imageView, String artist) {
        // Configurações do Glide para imagem circular
        RequestOptions requestOptions = new RequestOptions()
                .transform(new CircleCrop())
                .placeholder(R.drawable.ic_lm_capa_placeholder)
                .error(R.drawable.ic_lm_capa_placeholder);

        // Carrega a imagem - por enquanto usa placeholder
        // Pode ser expandido para buscar imagens reais de APIs
        Glide.with(context)
                .load(R.drawable.ic_lm_capa_placeholder)
                .apply(requestOptions)
                .into(imageView);
    }

    /**
     * ViewHolder para otimizar a exibição de itens na lista de artistas.
     */
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name; // TextView para o nome do artista
        ImageView artistImage; // ImageView para a imagem do artista

        ViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.artist_name);
            artistImage = itemView.findViewById(R.id.artist_image);
        }
    }

    /**
     * Interface para callback de cliques em artistas.
     */
    public interface OnArtistClickListener {
        void onArtistClick(String artist); // Método chamado ao clicar em um artista
    }
}