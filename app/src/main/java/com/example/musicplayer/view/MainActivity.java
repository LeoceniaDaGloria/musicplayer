package com.example.musicplayer.view;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.musicplayer.R;
import com.example.musicplayer.adapter.PlaylistAdapter;
import com.example.musicplayer.adapter.SongAdapter;
import com.example.musicplayer.model.Playlist;
import com.example.musicplayer.model.Song;
import com.example.musicplayer.presenter.MusicPresenter;
import com.example.musicplayer.services.MusicService;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity principal que implementa a View na MVP.
 * Gerencia UI, permissões e binding com serviço.
 */
public class MainActivity extends AppCompatActivity implements MusicView {
    private static final int REQUEST_CODE_PERMISSIONS = 100;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS
    };
    private static final String TAG = "MainActivity";

    private MusicPresenter presenter;
    private ImageView albumCover;
    private TextView songTitle, artistName, tvTempoAtual, tvDuracaoTotal;
    private ImageButton playPauseButton, prevButton, nextButton, shuffleButton, repeatButton;
    private SeekBar songProgress;
    private RecyclerView recyclerViewSongs, recyclerViewPlaylists;
    private Button scheduleAlarmButton, scheduleUpdateButton;
    private Handler handler = new Handler();
    private ObjectAnimator rotateAnimator;
    private MusicService musicService;
    private boolean isBound = false;
    private Toolbar toolbar;

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "Service connected");
            musicService = ((MusicService.MusicBinder) service).getService();
            isBound = true;
            presenter.setMusicService(musicService);
            enableControls(true);
            updateUIFromService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "Service disconnected");
            isBound = false;
            musicService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        presenter = new MusicPresenter(this, this, this);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        albumCover = findViewById(R.id.album_cover);
        songTitle = findViewById(R.id.song_title);
        artistName = findViewById(R.id.artist_name);
        tvTempoAtual = findViewById(R.id.tv_tempo_atual);
        tvDuracaoTotal = findViewById(R.id.tv_duracao_total);
        playPauseButton = findViewById(R.id.playPauseButton);
        prevButton = findViewById(R.id.prevButton);
        nextButton = findViewById(R.id.nextButton);
        shuffleButton = findViewById(R.id.shuffleButton);
        repeatButton = findViewById(R.id.repeatButton);
        songProgress = findViewById(R.id.song_progress);
        recyclerViewSongs = findViewById(R.id.recyclerViewSongs);
        recyclerViewPlaylists = findViewById(R.id.recyclerViewPlaylists);
        scheduleAlarmButton = findViewById(R.id.scheduleAlarmButton);
        scheduleUpdateButton = findViewById(R.id.scheduleUpdateButton);

        recyclerViewSongs.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewPlaylists.setLayoutManager(new LinearLayoutManager(this));

        setupListeners();
        setupAnimation();
        checkPermissions();

        Intent intent = new Intent(this, MusicService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
        startService(intent);

        // Inserir dados iniciais para teste
        if (presenter.getSongList().isEmpty()) {
            presenter.insertInitialData();
        }

        // Atualização em tempo real da SeekBar
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateUIFromService();
                handler.postDelayed(this, 1000);
            }
        }, 1000);

        presenter.scheduleMusicUpdate();

        Log.d(TAG, "onCreate completed");
    }

    private void setupListeners() {
        playPauseButton.setOnClickListener(v -> playPause());
        prevButton.setOnClickListener(v -> prevSong());
        nextButton.setOnClickListener(v -> nextSong());
        shuffleButton.setOnClickListener(v -> toggleShuffle());
        repeatButton.setOnClickListener(v -> toggleRepeat());

        songProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) seekTo(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        if (scheduleAlarmButton != null) {
            scheduleAlarmButton.setOnClickListener(v -> {
                long time = System.currentTimeMillis() + 300000; // 5 min
                presenter.schedulePlaybackAlarm(time);
                Toast.makeText(this, "Alarme agendado para playback", Toast.LENGTH_SHORT).show();
            });
        }
        if (scheduleUpdateButton != null) {
            scheduleUpdateButton.setOnClickListener(v -> {
                presenter.scheduleMusicUpdate();
                Toast.makeText(this, "Atualização periódica agendada", Toast.LENGTH_SHORT).show();
            });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) { return false; }
            @Override
            public boolean onQueryTextChange(String newText) {
                searchSongs(newText);
                return true;
            }
        });
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_favoritas) {
            presenter.loadFavorites();
            Toast.makeText(this, "Mostrando Favoritas", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_descarregadas) {
            // Placeholder: filtrar músicas locais
            presenter.loadDownloadedSongs();
            Toast.makeText(this, "Abrindo Descarregadas", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_artistas) {
            Intent intent = new Intent(this, ArtistsActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_recognize) {
            recognizeMusic();
            return true;
        } else if (id == R.id.action_create_playlist) {
            createPlaylist();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupAnimation() {
        rotateAnimator = ObjectAnimator.ofFloat(albumCover, "rotation", 0f, 360f);
        rotateAnimator.setDuration(10000);
        rotateAnimator.setInterpolator(new LinearInterpolator());
        rotateAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        rotateAnimator.setRepeatMode(ObjectAnimator.RESTART);
    }

    private void checkPermissions() {
        List<String> missingPermissions = new ArrayList<>();
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }
        if (!missingPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toArray(new String[0]), REQUEST_CODE_PERMISSIONS);
        } else {
            presenter.loadSongs();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                presenter.loadSongs();
            } else {
                Toast.makeText(this, "Permissões necessárias não concedidas", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void enableControls(boolean enable) {
        playPauseButton.setEnabled(enable);
        prevButton.setEnabled(enable);
        nextButton.setEnabled(enable);
        songProgress.setEnabled(enable);
    }

    @Override
    public void updateSongInfo(String title, String artist) {
        songTitle.setText(title);
        artistName.setText(artist);
        Glide.with(this).load(R.drawable.ic_launcher_foreground).into(albumCover);
    }

    @Override
    public void updatePlayPauseIcon(boolean isPlaying) {
        playPauseButton.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
        if (isPlaying) {
            rotateAnimator.start();
        } else {
            rotateAnimator.pause();
        }
        TransitionManager.beginDelayedTransition(findViewById(android.R.id.content), new AutoTransition());
    }

    @Override
    public void updateProgress(int progress, int duration) {
        songProgress.setMax(duration > 0 ? duration : 0);
        songProgress.setProgress(progress >= 0 ? progress : 0);
        tvTempoAtual.setText(formatTime(progress));
        tvDuracaoTotal.setText(formatTime(duration));
    }

    @Override
    public void updateSongList(List<Song> songs) {
        SongAdapter adapter = new SongAdapter(songs, song -> {
            Log.d(TAG, "Song clicked: " + song.getTitle());
            presenter.playSong(song);
        }, presenter);
        recyclerViewSongs.setAdapter(adapter);
        Log.d(TAG, "Updated song list with " + songs.size() + " items");
    }

    @Override
    public void updatePlaylistList(List<Playlist> playlists) {
        PlaylistAdapter adapter = new PlaylistAdapter(playlists, playlist -> presenter.loadPlaylistSongs(playlist));
        recyclerViewPlaylists.setAdapter(adapter);
        Log.d(TAG, "Updated playlist list with " + playlists.size() + " items");
    }

    @Override
    public void showRecognitionResult(String result) {
        Log.d(TAG, "Recognition result: " + result);
    }

    @Override
    public void requestPermissions() {
        checkPermissions();
    }

    @Override
    public void playSong(Song song) {
        presenter.playSong(song);
    }

    @Override
    public void playPause() {
        presenter.playPause();
    }

    @Override
    public void prevSong() {
        presenter.prevSong();
    }

    @Override
    public void nextSong() {
        presenter.nextSong();
    }

    @Override
    public void toggleShuffle() {
        presenter.toggleShuffle();
    }

    @Override
    public void toggleRepeat() {
        presenter.toggleRepeat();
    }

    @Override
    public void seekTo(int progress) {
        presenter.seekTo(progress);
    }

    @Override
    public void searchSongs(String query) {
        presenter.searchSongs(query);
    }

    @Override
    public void createPlaylist() {
        presenter.createPlaylist();
    }

    @Override
    public void recognizeMusic() {
        presenter.recognizeMusic();
    }

    @Override
    public void loadPlaylistSongs(Playlist playlist) {
        presenter.loadPlaylistSongs(playlist);
    }

    @Override
    public void updateArtists(List<String> artists) {
        // Não aplicável para esta Activity, pois foca em controle geral e navegação
    }

    private String formatTime(int ms) {
        if (ms <= 0) return "00:00";
        int sec = (ms / 1000) % 60;
        int min = (ms / 1000) / 60;
        return String.format("%02d:%02d", min, sec);
    }

    private void updateLastPlayedSong() {
        if (musicService != null && musicService.getCurrentSong() != null) {
            Song lastSong = musicService.getCurrentSong();
            updateSongInfo(lastSong.getTitle(), lastSong.getArtist());
            Log.d(TAG, "Displaying last played song: " + lastSong.getTitle());
        }
    }

    private void updateUIFromService() {
        if (musicService != null) {
            Song currentSong = musicService.getCurrentSong();
            if (currentSong != null) {
                updateSongInfo(currentSong.getTitle(), currentSong.getArtist());
                updatePlayPauseIcon(musicService.isPlaying());
                updateProgress((int) musicService.getCurrentPosition(), (int) musicService.getDuration());
            }
        }
    }

    @Override
    protected void onDestroy() {
        presenter.onDestroy();
        if (isBound) unbindService(connection);
        handler.removeCallbacksAndMessages(null);
        if (rotateAnimator != null) rotateAnimator.cancel();
        super.onDestroy();
        Log.d(TAG, "onDestroy called");
    }
}