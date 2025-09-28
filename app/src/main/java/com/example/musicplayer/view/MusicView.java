package com.example.musicplayer.view;

import com.example.musicplayer.model.Playlist;
import com.example.musicplayer.model.Song;

import java.util.List;

/**
 * Interface para a View na MVP.
 * Define métodos para atualizar UI e ações do usuário.
 */
public interface MusicView {
    /**
     * Atualiza título e artista na UI.
     * @param title Título.
     * @param artist Artista.
     */
    void updateSongInfo(String title, String artist);

    /**
     * Atualiza ícone de play/pause.
     * @param isPlaying True se tocando.
     */
    void updatePlayPauseIcon(boolean isPlaying);

    /**
     * Atualiza progresso do SeekBar.
     * @param progress Progresso atual.
     * @param duration Duração total.
     */
    void updateProgress(int progress, int duration);

    /**
     * Atualiza lista de músicas na UI.
     * @param songs Lista de músicas.
     */
    void updateSongList(List<Song> songs);

    /**
     * Atualiza lista de playlists na UI.
     * @param playlists Lista de playlists.
     */
    void updatePlaylistList(List<Playlist> playlists);

    /**
     * Mostra resultado de reconhecimento.
     * @param result Texto do resultado.
     */
    void showRecognitionResult(String result);

    /**
     * Solicita permissões ao usuário.
     */
    void requestPermissions();

    // Métodos de controle de reprodução (chamados pela View)
    void playSong(Song song);
    void playPause();
    void prevSong();
    void nextSong();
    void toggleShuffle();
    void toggleRepeat();
    /**
     * Move o progresso da reprodução para a posição especificada.
     * @param progress Progresso em milissegundos (deve ser validado contra duration pelo Presenter).
     */
    void seekTo(int progress);

    // Métodos de busca e interação
    void searchSongs(String query);
    void createPlaylist();
    void recognizeMusic();

    // Método para carregar músicas de playlist
    void loadPlaylistSongs(Playlist playlist);
    void updateArtists(List<String> artists);
}