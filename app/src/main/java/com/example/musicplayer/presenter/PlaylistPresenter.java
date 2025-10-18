// PlaylistPresenter.java - na pasta presenter/
package com.example.musicplayer.presenter;

import com.example.musicplayer.db.MusicRepository;
import com.example.musicplayer.model.Playlist;
import com.example.musicplayer.model.Song;
import com.example.musicplayer.view.PlaylistContract;

import java.util.List;

public class PlaylistPresenter implements PlaylistContract.Presenter {
    private PlaylistContract.View view;
    private MusicRepository repository;

    public PlaylistPresenter(PlaylistContract.View view, MusicRepository repository) {
        this.view = view;
        this.repository = repository;
    }

    @Override
    public void loadAllPlaylists() {
        view.showLoading();
        repository.getAllPlaylists().observeForever(playlists -> {
            view.hideLoading();
            view.showPlaylists(playlists);
        });
    }

    @Override
    public void loadPlaylistSongs(int playlistId) {
        view.showLoading();
        repository.getSongsForPlaylist((long) playlistId).observeForever(songs -> {
            view.hideLoading();
            view.showPlaylistSongs(songs);
        });
    }
    @Override
    public void createPlaylist(String name, String description) {
        if (name == null || name.trim().isEmpty()) {
            //Toast na UI Thread
            if (view instanceof android.app.Activity) {
                android.app.Activity activity = (android.app.Activity) view;
                activity.runOnUiThread(() -> view.showError("Nome da playlist não pode estar vazio"));
            } else {
                view.showError("Nome da playlist não pode estar vazio");
            }
            return;
        }

        new Thread(() -> {
            List<Playlist> existingPlaylists = repository.getAllPlaylistsSync();
            for (Playlist playlist : existingPlaylists) {
                if (playlist.getName().equalsIgnoreCase(name.trim())) {
                    //CORREÇÃO: Toast na UI Thread
                    if (view instanceof android.app.Activity) {
                        android.app.Activity activity = (android.app.Activity) view;
                        activity.runOnUiThread(() -> view.showError("Já existe uma playlist com esse nome"));
                    } else {
                        view.showError("Já existe uma playlist com esse nome");
                    }
                    return;
                }
            }

            // Cria a nova playlist
            Playlist newPlaylist = new Playlist();
            newPlaylist.setName(name.trim());

            repository.insertPlaylist(newPlaylist);

            //Executar na UI Thread
            if (view instanceof android.app.Activity) {
                android.app.Activity activity = (android.app.Activity) view;
                activity.runOnUiThread(() -> view.onPlaylistCreated(newPlaylist));
            } else {
                view.onPlaylistCreated(newPlaylist);
            }
        }).start();
    }

    @Override
    public void addSongsToPlaylist(int playlistId, List<Song> songs) {
        if (songs == null || songs.isEmpty()) {
            // Toast na UI Thread
            if (view instanceof android.app.Activity) {
                android.app.Activity activity = (android.app.Activity) view;
                activity.runOnUiThread(() -> view.showError("Nenhuma música selecionada"));
            } else {
                view.showError("Nenhuma música selecionada");
            }
            return;
        }

        new Thread(() -> {
            for (Song song : songs) {
                if (!repository.isSongInPlaylist((long) playlistId, song)) {
                    repository.addSongToPlaylist((long) playlistId, song);
                }
            }

            //Executar na UI Thread
            if (view instanceof android.app.Activity) {
                android.app.Activity activity = (android.app.Activity) view;
                activity.runOnUiThread(() -> view.onSongAddedToPlaylist());
            } else {
                view.onSongAddedToPlaylist();
            }
        }).start();
    }

    @Override
    public void removeSongFromPlaylist(int playlistId, int songId) {
        new Thread(() -> {
            Song song = repository.getSongById((long) songId);
            if (song != null) {
                repository.removeSongFromPlaylist((long) playlistId, song);

                //Executar na UI Thread
                if (view instanceof android.app.Activity) {
                    android.app.Activity activity = (android.app.Activity) view;
                    activity.runOnUiThread(() -> view.onSongRemovedFromPlaylist());
                } else {
                    view.onSongRemovedFromPlaylist();
                }
            }
        }).start();
    }

    @Override
    public void deletePlaylist(int playlistId) {
        new Thread(() -> {
            Playlist playlist = repository.getPlaylistById((long) playlistId);
            if (playlist != null) {
                repository.deletePlaylist(playlist);

                // Executar na UI Thread
                if (view instanceof android.app.Activity) {
                    android.app.Activity activity = (android.app.Activity) view;
                    activity.runOnUiThread(() -> {
                        view.onPlaylistDeleted();
                    });
                } else {
                    // Fallback seguro
                    view.onPlaylistDeleted();
                }
            }
        }).start();
    }

    @Override
    public void updatePlaylistOrder(int playlistId, List<Song> reorderedSongs) {
        // Implementação opcional para reordenar
    }
}