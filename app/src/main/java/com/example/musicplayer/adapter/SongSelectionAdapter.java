package com.example.musicplayer.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.example.musicplayer.R;
import com.example.musicplayer.model.Song;

import java.util.ArrayList;
import java.util.List;

public class SongSelectionAdapter extends RecyclerView.Adapter<SongSelectionAdapter.SongSelectionViewHolder> {

    private Context context;
    private List<Song> songs;
    private List<Song> selectedSongs;
    private OnSelectionChangedListener selectionListener;

    public SongSelectionAdapter(Context context, List<Song> songs) {
        this.context = context;
        this.songs = songs != null ? songs : new ArrayList<>();
        this.selectedSongs = new ArrayList<>();
    }

    public interface OnSelectionChangedListener {
        void onSelectionChanged(List<Song> selectedSongs);
    }

    public void setSelectionListener(OnSelectionChangedListener listener) {
        this.selectionListener = listener;
    }

    public List<Song> getSelectedSongs() {
        return new ArrayList<>(selectedSongs);
    }

    public void setSongs(List<Song> songs) {
        this.songs = songs != null ? songs : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SongSelectionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_song_selection, parent, false);
        return new SongSelectionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SongSelectionViewHolder holder, int position) {
        Song song = songs.get(position);
        holder.bind(song);
    }

    @Override
    public int getItemCount() {
        return songs.size();
    }

    class SongSelectionViewHolder extends RecyclerView.ViewHolder {
        CheckBox selectionCheckbox;
        ImageView albumCover;
        TextView songTitle;
        TextView songArtist;
        TextView songDuration;

        public SongSelectionViewHolder(@NonNull View itemView) {
            super(itemView);
            selectionCheckbox = itemView.findViewById(R.id.selection_checkbox);
            albumCover = itemView.findViewById(R.id.album_cover);
            songTitle = itemView.findViewById(R.id.song_title);
            songArtist = itemView.findViewById(R.id.song_artist);
            songDuration = itemView.findViewById(R.id.song_duration);

            // Clique no checkbox
            selectionCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    Song song = songs.get(position);
                    if (isChecked) {
                        if (!selectedSongs.contains(song)) {
                            selectedSongs.add(song);
                        }
                    } else {
                        selectedSongs.remove(song);
                    }

                    if (selectionListener != null) {
                        selectionListener.onSelectionChanged(selectedSongs);
                    }
                }
            });

            // Clique no item inteiro alterna o checkbox
            itemView.setOnClickListener(v -> {
                selectionCheckbox.setChecked(!selectionCheckbox.isChecked());
            });
        }

        public void bind(Song song) {
            songTitle.setText(song.getTitle());
            songArtist.setText(song.getArtist());

            // Formatar duração
            String duration = formatDuration(song.getDuration());
            songDuration.setText(duration);

            // Verificar se está selecionado
            selectionCheckbox.setChecked(selectedSongs.contains(song));

            // Carregar capa do álbum
            loadAlbumArt(song);
        }

        private String formatDuration(int durationMs) {
            if (durationMs <= 0) return "0:00";

            int minutes = (durationMs / 1000) / 60;
            int seconds = (durationMs / 1000) % 60;
            return String.format("%d:%02d", minutes, seconds);
        }

        private void loadAlbumArt(Song song) {
            RequestOptions requestOptions = new RequestOptions()
                    .transform(new CenterCrop(), new RoundedCorners(12))
                    .placeholder(R.drawable.ic_lm_capa_placeholder)
                    .error(R.drawable.ic_lm_capa_placeholder);

            Glide.with(context)
                    .load(R.drawable.ic_lm_capa_placeholder) // Placeholder inicial
                    .apply(requestOptions)
                    .into(albumCover);

            // Em produção, você extrairia a capa real do arquivo de música
        }
    }
}