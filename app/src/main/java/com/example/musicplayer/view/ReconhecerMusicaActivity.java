/*
package com.example.musicplayer.view;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.acrcloud.recognition.AcrCloudRecognizeClient;
import com.acrcloud.recognition.listener.IAcrCloudListener;
import com.example.musicplayer.R;
import com.example.musicplayer.model.Playlist;
import com.example.musicplayer.presenter.MusicPresenter;
import com.example.musicplayer.utils.ACRCloudHelper;

import org.json.JSONArray;
import org.json.JSONObject;


public class ReconhecerMusicaActivity extends AppCompatActivity implements MusicView {
    private static final String TAG = "ReconhecerMusicaActivity";
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static final int SAMPLE_RATE = 16000; // Recomendado pelo ACRCloud
    private static final int RECORDING_DURATION_MS = 10000; // 10 segundos
    private MusicPresenter presenter;
    private ImageButton recognizeButton;
    private TextView resultText;
    private ProgressBar progressBar;
    private AudioRecord audioRecord;
    private Handler handler = new Handler();
    private boolean isRecording = false;
    private byte[] audioBuffer;
    private AcrCloudRecognizeClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reconhecer_musica);

        presenter = new MusicPresenter(this, this, this);

        recognizeButton = findViewById(R.id.recognize_button);
        resultText = findViewById(R.id.result_text);
        progressBar = findViewById(R.id.progress_bar);

        // TODO: Reativar o listener quando o reconhecimento for implementado
        // recognizeButton.setOnClickListener(v -> startRecognition());

        checkAudioPermission();
    }

    private void checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permissão de áudio concedida", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permissão de áudio necessária", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // TODO: Reativar e completar a lógica de reconhecimento quando for o momento
    /*
    private void startRecognition() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (!isRecording) {
                isRecording = true;
                recognizeButton.setEnabled(false);
                progressBar.setVisibility(View.VISIBLE);
                resultText.setText("Reconhecendo música...");

                new Thread(() -> {
                    recordAudio();
                    handler.post(() -> {
                        sendToACRCloud();
                        isRecording = false;
                        recognizeButton.setEnabled(true);
                        progressBar.setVisibility(View.GONE);
                    });
                }).start();
            }
        } else {
            checkAudioPermission();
        }
    }

    private void recordAudio() {
        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        audioBuffer = new byte[bufferSize * (RECORDING_DURATION_MS / 100)]; // Buffer para 10s
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord não inicializado");
            return;
        }

        try {
            audioRecord.startRecording();
            int bytesRead = audioRecord.read(audioBuffer, 0, audioBuffer.length);
            Log.d(TAG, "Gravado " + bytesRead + " bytes de áudio");
        } catch (Exception e) {
            Log.e(TAG, "Erro ao gravar áudio: " + e.getMessage());
        } finally {
            if (audioRecord != null) {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
            }
        }
    }

    private void sendToACRCloud() {
        client = ACRCloudHelper.getClient();
        if (client == null || !ACRCloudHelper.initState) {
            showRecognitionResult("Erro: Inicialização do cliente falhou");
            return;
        }

        client.startRecognize(new IAcrCloudListener() {
            @Override
            public void onResult(String result) {
                handler.post(() -> {
                    try {
                        String tres = "\n";
                        JSONObject json = new JSONObject(result);
                        JSONObject status = json.getJSONObject("status");
                        int code = status.getInt("code");
                        if (code == 0) {
                            JSONObject metadata = json.getJSONObject("metadata");
                            if (metadata.has("music")) {
                                JSONArray musics = metadata.getJSONArray("music");
                                for (int i = 0; i < musics.length(); i++) {
                                    JSONObject music = musics.getJSONObject(i);
                                    String title = music.getString("title");
                                    String artist = music.getJSONArray("artists").getJSONObject(0).getString("name");
                                    tres += (i + 1) + ". Artista: " + artist + ", Título: " + title + "\n";
                                }
                            }
                            showRecognitionResult(tres);
                        } else {
                            showRecognitionResult("Erro: " + result);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Erro ao processar resposta: " + e.getMessage());
                        showRecognitionResult("Erro ao processar resposta");
                    }
                });
            }

            @Override
            public void onVolumeChanged(double volume) {
                // Opcional: atualizar UI com volume (ex.: TextView)
            }
        });

        if (!client.startRecognize(audioBuffer, audioBuffer.length, SAMPLE_RATE, "pcm", 16, 1)) {
            showRecognitionResult("Erro: Falha ao iniciar reconhecimento");
        }
    }


    @Override
    public void showRecognitionResult(String result) {
        resultText.setText(result);
        Log.d(TAG, "Recognition result: " + result);

        // Retorna à música anterior
        if (presenter != null && presenter.getMusicService() != null && presenter.getMusicService().getCurrentSong() != null) {
            presenter.playSong(presenter.getMusicService().getCurrentSong());
        }
    }

    // Outros métodos da MusicView (não aplicáveis, implementados como vazios)
    @Override
    public void updateSongList(List<Song> songs) { }
    @Override
    public void updatePlaylistList(List<Playlist> playlists) { }
    @Override
    public void updateSongInfo(String title, String artist) { }
    @Override
    public void updatePlayPauseIcon(boolean isPlaying) { }
    @Override
    public void updateProgress(int progress, int duration) { }
    @Override
    public void requestPermissions() { checkAudioPermission(); }
    @Override
    public void playSong(Song song) { }
    @Override
    public void playPause() { }
    @Override
    public void prevSong() { }
    @Override
    public void nextSong() { }
    @Override
    public void toggleShuffle() { }
    @Override
    public void toggleRepeat() { }
    @Override
    public void seekTo(int progress) { }
    @Override
    public void searchSongs(String query) { }
    @Override
    public void createPlaylist() { }
    @Override
    public void recognizeMusic() { }
    @Override
    public void loadPlaylistSongs(Playlist playlist) { }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (audioRecord != null) {
            audioRecord.release();
            audioRecord = null;
        }
        if (client != null) {
            client.stopRecognize(); // Liberar recursos do ACRCloud
        }
        handler.removeCallbacksAndMessages(null);
    }
}

     */