// PlaylistContract.java - na pasta view/
package com.example.musicplayer.view;

import com.example.musicplayer.model.Playlist;
import com.example.musicplayer.model.Song;
import java.util.List;

public interface PlaylistContract {
    interface View {
        void showPlaylists(List<Playlist> playlists);
        void showPlaylistSongs(List<Song> songs);
        void onPlaylistCreated(Playlist playlist);
        void onPlaylistDeleted();
        void onSongAddedToPlaylist();
        void onSongRemovedFromPlaylist();
        void showError(String message);
        void showLoading();
        void hideLoading();
    }

    interface Presenter {
        void loadAllPlaylists();
        void loadPlaylistSongs(int playlistId);
        void createPlaylist(String name, String description);
        void addSongsToPlaylist(int playlistId, List<Song> songs);
        void removeSongFromPlaylist(int playlistId, int songId);
        void deletePlaylist(int playlistId);
        void updatePlaylistOrder(int playlistId, List<Song> reorderedSongs);
    }
}