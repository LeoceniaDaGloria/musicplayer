package com.example.musicplayer.view;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Bitmap;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.musicplayer.R;
import com.example.musicplayer.model.Playlist;
import com.example.musicplayer.model.Song;
import com.example.musicplayer.presenter.MusicPresenter;
import com.example.musicplayer.utils.ACRCloudHelper;
import com.example.musicplayer.utils.AudioRecorderHelper;

import java.util.List;

/**
 * Activity para reconhecimento de música usando ACRCloud
 */
public class ReconhecerMusicaActivity extends AppCompatActivity implements com.example.musicplayer.view.MusicView, MusicPresenter.OnMusicOperationListener {
    private static final String TAG = "ReconhecerMusicaActivity";
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

    private MusicPresenter presenter;
    private ImageView recognizeButton;
    private TextView resultText;
    private ProgressBar progressBar;
    private Button btnPlayRecognized;
    private Button btnBack;
    private Toolbar toolbar;

    private AudioRecorderHelper audioRecorder;
    private Handler handler = new Handler();

    // Variáveis para armazenar resultado do reconhecimento
    private String recognizedTitle = "";
    private String recognizedArtist = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reconhecer_musica);

        // ✅ DEBUG: INICIAL
        Log.d(TAG, "=== INICIANDO RECONHECER MUSICA ACTIVITY ===");

        // Configura a Toolbar
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle("Reconhecer Música");
        }

        toolbar.setNavigationOnClickListener(v -> {
            Log.d(TAG, "Botão voltar da Toolbar clicado");
            onBackPressed();
        });

        // ✅ CORREÇÃO: CONFIGURAR O BOTÃO VOLTAR DO LAYOUT
        btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                Log.d(TAG, "✅ Botão Voltar clicado - Finalizando activity");
                finish();
            });
            Log.d(TAG, "✅ Botão Voltar configurado com sucesso");
        } else {
            Log.e(TAG, "❌ Botão Voltar não encontrado! Verifique o ID no XML");
        }

        presenter = new MusicPresenter(this, this, this);

        // Configurar listener para operações
        presenter.setOperationListener(this);

        audioRecorder = new AudioRecorderHelper(this);

        initializeViews();
        setupClickListeners();

        // ✅ DEBUG: TESTE DE GRAVAÇÃO
        testAudioRecording();

        // ✅ DEBUG: TESTE ACRCLOUD
        testACRCloudCredentials();

        checkAudioPermission();

        // ✅ DEBUG: VERIFICAR TODOS OS BOTÕES
        debugButtons();

        Log.d(TAG, "=== ACTIVITY INICIALIZADA COM SUCESSO ===");
    }

    /**
     * ✅ DEBUG: VERIFICAR TODOS OS BOTÕES
     */
    private void debugButtons() {
        Log.d(TAG, "=== DEBUG DE BOTÕES ===");

        Button btnBack = findViewById(R.id.btn_back);
        Button btnPlay = findViewById(R.id.btn_play_recognized);

        Log.d(TAG, "Botão Voltar: " + (btnBack != null ? "ENCONTRADO" : "NÃO ENCONTRADO"));
        Log.d(TAG, "Botão Play: " + (btnPlay != null ? "ENCONTRADO" : "NÃO ENCONTRADO"));

        if (btnBack != null) {
            Log.d(TAG, "Botão Voltar - Texto: " + btnBack.getText());
            Log.d(TAG, "Botão Voltar - Habilitado: " + btnBack.isEnabled());
            Log.d(TAG, "Botão Voltar - Clicável: " + btnBack.isClickable());
            Log.d(TAG, "Botão Voltar - Visível: " + (btnBack.getVisibility() == View.VISIBLE));
        }

        Log.d(TAG, "=== FIM DEBUG ===");
    }

    /**
     * ✅ DEBUG: TESTE DE GRAVAÇÃO DE ÁUDIO
     */
    private void testAudioRecording() {
        Log.d(TAG, "=== TESTE DE GRAVAÇÃO DE ÁUDIO ===");
        Log.d(TAG, "AudioRecorderHelper inicializado: " + (audioRecorder != null));
        Log.d(TAG, "Tem permissão de áudio: " + audioRecorder.hasAudioPermission());
        Log.d(TAG, "Está gravando atualmente: " + audioRecorder.isRecording());
        Log.d(TAG, "=== FIM DO TESTE DE GRAVAÇÃO ===");
    }

    /**
     * ✅ DEBUG: TESTE DE CREDENCIAIS ACRCLOUD
     */
    private void testACRCloudCredentials() {
        Log.d(TAG, "=== TESTE ACRCLOUD CREDENCIAIS ===");
        // Vamos simular um teste básico das credenciais
        new Thread(() -> {
            try {
                // Teste com dados mínimos
                byte[] testData = new byte[1000]; // 1KB de dados de teste
                String testResult = ACRCloudHelper.recognizeMusic(testData, this);

                runOnUiThread(() -> {
                    Log.d(TAG, "Resultado do teste ACRCloud: " + testResult);

                    if (testResult.contains("3014") || testResult.contains("Credenciais inválidas")) {
                        resultText.setText("PROBLEMA NAS CREDENCIAIS ACRCLOUD!\n\n" +
                                "Erro 3014 detectado.\n" +
                                "Verifique Access Key e Secret no ACRCloudHelper.java");
                    } else if (testResult.contains("Erro de conexão")) {
                        resultText.setText("PROBLEMA DE CONEXÃO\n\n" +
                                "Verifique sua internet e tente novamente.");
                    } else {
                        Log.d(TAG, "Credenciais ACRCloud parecem válidas");
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Erro no teste ACRCloud: " + e.getMessage());
            }
        }).start();
        Log.d(TAG, "=== FIM DO TESTE ACRCLOUD ===");
    }

    /**
     * Obtém cor baseada no tema atual (claro/escuro)
     */
    private int getColorForTheme(@ColorRes int lightColor, @ColorRes int darkColor) {
        int currentNightMode = getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;

        if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
            return ContextCompat.getColor(this, darkColor);
        } else {
            return ContextCompat.getColor(this, lightColor);
        }
    }

    /**
     * Aplica cores dinâmicas aos itens do menu baseado no tema (claro/escuro)
     */
    private void applyDynamicMenuColors(Menu menu) {
        try {
            // Obter a cor dinâmica para ícones
            int menuIconColor = getColorForTheme(
                    R.color.icon_primary_light,
                    R.color.icon_primary_dark
            );

            // Obter cor para texto (opcional)
            int menuTextColor = getColorForTheme(
                    R.color.text_primary_light,
                    R.color.text_primary_dark
            );

            Log.d(TAG, "Aplicando cores dinâmicas ao menu - Cor: " + Integer.toHexString(menuIconColor));

            // Aplicar cor a todos os itens do menu com ícones
            for (int i = 0; i < menu.size(); i++) {
                MenuItem item = menu.getItem(i);

                // Aplicar cor ao ícone se existir
                if (item.getIcon() != null) {
                    // Método 1: Usar setColorFilter (funciona bem)
                    item.getIcon().setColorFilter(menuIconColor, android.graphics.PorterDuff.Mode.SRC_IN);

                    // Método 2: Alternativa usando tint (Android 8.0+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        item.getIcon().setTint(menuIconColor);
                    }

                    Log.d(TAG, "Cor aplicada ao ícone: " + item.getTitle());
                }

                // Opcional: Aplicar cor ao texto do menu (para overflow)
                SpannableString spanString = new SpannableString(item.getTitle());
                spanString.setSpan(new ForegroundColorSpan(menuTextColor), 0, spanString.length(), 0);
                item.setTitle(spanString);
            }

        } catch (Exception e) {
            Log.e(TAG, "Erro ao aplicar cores dinâmicas ao menu: " + e.getMessage());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);

        // CONFIGURAÇÃO DE CORES DINÂMICAS DO MENU
        applyDynamicMenuColors(menu);


        // CONFIGURAÇÃO DO SEARCHVIEW
        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();

        if (searchView != null) {
            // PARA DECLARAR VARIÁVEIS FORA DO BLOCO TRY
            int hintColor;
            int fallbackHintColor = Color.LTGRAY;

            // Para Usar cores dinâmicas do sistema
            try {
                // Usar IDs do AppCompat
                EditText searchEditText = searchView.findViewById(androidx.appcompat.R.id.search_src_text);
                if (searchEditText != null) {
                    // ✅ CORES PARA MODO ESCURO
                    int textColor = Color.WHITE;
                    hintColor = Color.LTGRAY;

                    searchEditText.setTextColor(textColor);
                    searchEditText.setHintTextColor(hintColor);

                    Log.d(TAG, "SearchView configurado com cores para modo escuro");

                    // ✅ CONFIGURAR CORES DOS ÍCONES TAMBÉM
                    ImageView searchIcon = searchView.findViewById(androidx.appcompat.R.id.search_mag_icon);
                    if (searchIcon != null) {
                        searchIcon.setColorFilter(hintColor);
                    }

                    ImageView closeIcon = searchView.findViewById(androidx.appcompat.R.id.search_close_btn);
                    if (closeIcon != null) {
                        closeIcon.setColorFilter(hintColor);
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "Erro ao configurar cor do SearchView: " + e.getMessage());

                // ✅ FALLBACK SIMPLES
                try {
                    EditText searchEditText = searchView.findViewById(androidx.appcompat.R.id.search_src_text);
                    if (searchEditText != null) {
                        // Cores fixas como fallback
                        searchEditText.setTextColor(Color.WHITE);
                        searchEditText.setHintTextColor(fallbackHintColor);

                        // Configurar ícones no fallback também
                        ImageView searchIcon = searchView.findViewById(androidx.appcompat.R.id.search_mag_icon);
                        if (searchIcon != null) {
                            searchIcon.setColorFilter(fallbackHintColor);
                        }

                        ImageView closeIcon = searchView.findViewById(androidx.appcompat.R.id.search_close_btn);
                        if (closeIcon != null) {
                            closeIcon.setColorFilter(fallbackHintColor);
                        }
                    }
                } catch (Exception fallbackEx) {
                    Log.e(TAG, "Erro no fallback do SearchView: " + fallbackEx.getMessage());
                }
            }

            // Configurar hint
            searchView.setQueryHint("Pesquisar músicas...");

            // Listener para pesquisa
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    searchSongs(query);
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    searchSongs(newText);
                    return true;
                }
            });
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_favoritas) {
            navigateTo(FavoritasActivity.class);
            return true;
        } else if (id == R.id.action_descarregadas) {
            navigateTo(DescarregadasActivity.class);
            return true;
        } else if (id == R.id.action_artistas) {
            navigateTo(ArtistsActivity.class);
            return true;
        } else if (id == R.id.action_recognize) {
            // Já está na ReconhecerMusica, não faz nada
            return true;
        } else if (id == R.id.action_playlists) {
            navigateTo(PlaylistsActivity.class);
            return true;
        } else if (id == android.R.id.home) {
            Log.d(TAG, "Botão home da toolbar clicado");
            onBackPressed();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void navigateTo(Class<?> activityClass) {
        try {
            if (this.getClass().equals(activityClass)) {
                return; // Já está na activity
            }
            Intent intent = new Intent(this, activityClass);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao navegar: " + e.getMessage());
        }
    }

    private void initializeViews() {
        recognizeButton = findViewById(R.id.recognize_button);
        resultText = findViewById(R.id.result_text);
        progressBar = findViewById(R.id.progress_bar);
        btnPlayRecognized = findViewById(R.id.btn_play_recognized);

        // Estado inicial
        btnPlayRecognized.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);

        // APLICAR COR DINÂMICA NO BOTÃO SHAZAM
        setupShazamButtonColor();

        // DEBUG: VIEWS INICIALIZADAS
        Log.d(TAG, "Views inicializadas - RecognizeButton: " + (recognizeButton != null));
        Log.d(TAG, "Views inicializadas - ResultText: " + (resultText != null));
        Log.d(TAG, "Views inicializadas - ProgressBar: " + (progressBar != null));
        Log.d(TAG, "Views inicializadas - BtnPlay: " + (btnPlayRecognized != null));
    }

    /**
     *  APLICA CORES DINÂMICAS (AGORA FEITO AUTOMATICAMENTE PELO XML)
     */
    private void setupShazamButtonColor() {
        //  O XML já cuida das cores dinâmicas usando ?attr/colorOnSurface
        // Não precisa fazer nada no código!

        Log.d(TAG, " Botão Shazam configurado com cores dinâmicas via XML");

        // Apenas para debug - verificar se está funcionando
        if (recognizeButton != null) {
            Log.d(TAG, " Botão Shazam - Tema atual: " +
                    (isLightTheme() ? "Claro (Preto)" : "Escuro (Branco)"));
        }
    }

    /**
     * ✅ VERIFICA SE É MODO CLARO
     */
    private boolean isLightTheme() {
        int nightModeFlags = getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == Configuration.UI_MODE_NIGHT_NO;
    }

    private void setupClickListeners() {
        recognizeButton.setOnClickListener(v -> startRecognition());

        btnPlayRecognized.setOnClickListener(v -> playRecognizedSong());

        //DEBUG: CLICK LISTENERS CONFIGURADOS
        Log.d(TAG, "Click listeners configurados");
    }

    private void checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO_PERMISSION);
            Log.d(TAG, "Solicitando permissão de áudio...");
        } else {
            Log.d(TAG, " Permissão de áudio já concedida");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        Log.d(TAG, "onRequestPermissionsResult - RequestCode: " + requestCode);

        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Permissão de áudio concedida pelo usuário");
                Toast.makeText(this, "Permissão de áudio concedida", Toast.LENGTH_SHORT).show();
                resultText.setText(" Permissão concedida!\nClique no botão para reconhecer música.");
            } else {
                Log.w(TAG, " Permissão de áudio negada pelo usuário");
                Toast.makeText(this, "Permissão de áudio necessária", Toast.LENGTH_LONG).show();
                resultText.setText(" Permissão de áudio negada.\n\n" +
                        "Para usar o reconhecimento de música:\n" +
                        "1. Vá em Configurações do App\n" +
                        "2. Ative a permissão de Microfone\n" +
                        "3. Volte e tente novamente");
            }
        }
    }

    private void startRecognition() {
        Log.d(TAG, "=== INICIANDO RECONHECIMENTO ===");

        if (!audioRecorder.hasAudioPermission()) {
            Log.w(TAG, " Sem permissão de áudio - solicitando...");
            checkAudioPermission();
            return;
        }

        if (audioRecorder.isRecording()) {
            Log.w(TAG, "Já está gravando - ignorando comando");
            Toast.makeText(this, "Já está reconhecendo...", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, " Condições OK - iniciando gravação...");

        // Atualizar UI
        recognizeButton.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        btnPlayRecognized.setVisibility(View.GONE);
        resultText.setText("🎤 Gravando áudio...\n(10 segundos)");

        Log.d(TAG, "UI atualizada - iniciando gravação de áudio");

        // Iniciar gravação
        audioRecorder.startRecording(new AudioRecorderHelper.RecordingCallback() {
            @Override
            public void onRecordingComplete(byte[] audioData) {
                Log.d(TAG, " Gravação concluída. Dados: " + audioData.length + " bytes");

                // DEBUG: QUALIDADE DO ÁUDIO
                if (audioData.length < 1000) {
                    Log.w(TAG, "⚠ Áudio muito curto: " + audioData.length + " bytes");
                } else {
                    Log.d(TAG, " Áudio com tamanho adequado: " + audioData.length + " bytes");
                }

                handler.post(() -> {
                    resultText.setText(" Analisando áudio...\nEnviando para reconhecimento");
                    Log.d(TAG, "Enviando áudio para ACRCloud...");
                    processAudioData(audioData);
                });
            }

            @Override
            public void onRecordingError(String error) {
                Log.e(TAG, " Erro na gravação: " + error);

                handler.post(() -> {
                    recognizeButton.setEnabled(true);
                    progressBar.setVisibility(View.GONE);
                    resultText.setText(" Erro na gravação: " + error);
                    Log.e(TAG, "Erro final na gravação: " + error);
                });
            }
        });
    }

    private void processAudioData(byte[] audioData) {
        Log.d(TAG, "Processando dados de áudio: " + audioData.length + " bytes");

        new Thread(() -> {
            try {
                Log.d(TAG, "=== ENVIANDO PARA ACRCLOUD ===");
                Log.d(TAG, "Tamanho do áudio: " + audioData.length + " bytes");

                // Chamar ACRCloud
                String result = ACRCloudHelper.recognizeMusic(audioData, this);

                Log.d(TAG, "=== RESPOSTA DO ACRCLOUD ===");
                Log.d(TAG, "Resultado: " + result);

                handler.post(() -> {
                    recognizeButton.setEnabled(true);
                    progressBar.setVisibility(View.GONE);

                    // ✅ DEBUG: ANÁLISE DO RESULTADO
                    if (result.contains("Música Reconhecida") || result.contains("✅")) {
                        Log.d(TAG, " MÚSICA RECONHECIDA COM SUCESSO!");
                        resultText.setText(result);
                        extractSongInfo(result);
                        btnPlayRecognized.setVisibility(View.VISIBLE);
                    } else if (result.contains("3014")) {
                        Log.e(TAG, " ERRO 3014 - CREDENCIAIS INVÁLIDAS");
                        resultText.setText(" ERRO DE CREDENCIAIS ACRCLOUD\n\n" +
                                "Código: 3014 - Credenciais inválidas\n\n" +
                                "Solução:\n" +
                                "1. Verifique se as credenciais no ACRCloudHelper.java estão corretas\n" +
                                "2. Confirme no site da ACRCloud se sua conta está ativa\n" +
                                "3. Verifique se não há espaços extras nas credenciais");
                    } else if (result.contains("3000") || result.contains("3001")) {
                        Log.e(TAG, " ERRO DE SERVIÇO ACRCLOUD");
                        resultText.setText(" SERVIÇO ACRCLOUD INDISPONÍVEL\n\n" +
                                "Tente novamente em alguns instantes.");
                    } else {
                        Log.w(TAG, " MÚSICA NÃO RECONHECIDA");
                        resultText.setText(result);
                        btnPlayRecognized.setVisibility(View.GONE);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, " Erro no processamento: " + e.getMessage(), e);

                handler.post(() -> {
                    recognizeButton.setEnabled(true);
                    progressBar.setVisibility(View.GONE);
                    resultText.setText(" Erro no processamento: " + e.getMessage());
                    btnPlayRecognized.setVisibility(View.GONE);
                    Log.e(TAG, "Erro final no processamento: " + e.getMessage());
                });
            }
        }).start();
    }

    private void extractSongInfo(String result) {
        try {
            Log.d(TAG, "Extraindo informações da música do resultado...");

            // Extrair título e artista do resultado
            String[] lines = result.split("\n");
            for (String line : lines) {
                if (line.startsWith("Título:") || line.contains("Título:")) {
                    recognizedTitle = line.replace("Título:", "").replace("🎵 Título:", "").trim();
                    Log.d(TAG, "Título extraído: " + recognizedTitle);
                } else if (line.startsWith("Artista:") || line.contains("Artista:")) {
                    recognizedArtist = line.replace("Artista:", "").replace("🎤 Artista:", "").trim();
                    Log.d(TAG, "Artista extraído: " + recognizedArtist);
                }
            }

            Log.d(TAG, " Música reconhecida: " + recognizedTitle + " - " + recognizedArtist);

        } catch (Exception e) {
            Log.e(TAG, " Erro ao extrair informações da música: " + e.getMessage());
        }
    }

    private void playRecognizedSong() {
        Log.d(TAG, "Tentando reproduzir música reconhecida...");

        if (!recognizedTitle.isEmpty() && !recognizedArtist.isEmpty()) {
            Log.d(TAG, "Criando música: " + recognizedTitle + " - " + recognizedArtist);

            // Criar uma música fictícia com base no reconhecimento
            Song recognizedSong = new Song(
                    recognizedTitle,
                    recognizedArtist,
                    "content://media/external/audio/media", // Path fictício
                    0,
                    0
            );

            // Reproduzir a música (se estiver na biblioteca)
            presenter.playSong(recognizedSong);

            Toast.makeText(this,
                    "Tocando: " + recognizedTitle + " - " + recognizedArtist,
                    Toast.LENGTH_LONG).show();

            Log.d(TAG, "Música enviada para reprodução");

            // Voltar para a main activity
            finish();
        } else {
            Log.w(TAG, " Nenhuma música reconhecida para reproduzir");
            Toast.makeText(this, "Nenhuma música reconhecida para reproduzir", Toast.LENGTH_SHORT).show();
        }
    }

    // ✅ MÉTODO onBackPressed MELHORADO
    @Override
    public void onBackPressed() {
        Log.d(TAG, "Botão back físico pressionado");

        // Parar gravação se estiver ativa
        if (audioRecorder != null && audioRecorder.isRecording()) {
            Log.d(TAG, "Parando gravação ativa antes de voltar...");
            audioRecorder.stopRecording();
        }

        // Remover callbacks do handler
        handler.removeCallbacksAndMessages(null);

        super.onBackPressed();
    }

    // ============================================================
    // MÉTODO updateAlbumArt - ADICIONADO PARA RESOLVER O ERRO
    // ============================================================

    @Override
    public void updateAlbumArt(Bitmap albumArt) {
        runOnUiThread(() -> {
            Log.d(TAG, "updateAlbumArt chamado - Capa recebida: " + (albumArt != null ? "Bitmap válido" : "null"));
            // Este método é necessário pela interface, mas não é usado nesta activity
            // Pode ser deixado vazio ou usado para debug
        });
    }

    // ============================================================
    // IMPLEMENTAÇÃO DO OnMusicOperationListener
    // ============================================================

    @Override
    public void onSongRenamed(Song song) {
        Log.d(TAG, "onSongRenamed chamado: " + song.getTitle());
        // Não é necessário fazer nada específico nesta activity
    }

    @Override
    public void onSongDeleted(Song song) {
        Log.d(TAG, "onSongDeleted chamado: " + song.getTitle());
        // Não é necessário fazer nada específico nesta activity
    }

    @Override
    public void onCoverChanged() {
        Log.d(TAG, "onCoverChanged chamado");
        // Não é necessário fazer nada específico nesta activity
    }

    // ============================================================
    // IMPLEMENTAÇÃO DOS MÉTODOS DA MusicView
    // ============================================================

    @Override
    public void showRecognitionResult(String result) {
        Log.d(TAG, "showRecognitionResult chamado: " + result);

        runOnUiThread(() -> {
            recognizeButton.setEnabled(true);
            progressBar.setVisibility(View.GONE);

            if (result != null && (result.contains("Música Reconhecida") || result.contains("✅"))) {
                resultText.setText(result);
                extractSongInfo(result);
                btnPlayRecognized.setVisibility(View.VISIBLE);

                Toast.makeText(this, "Música reconhecida com sucesso!", Toast.LENGTH_SHORT).show();
                Log.d(TAG, " Reconhecimento bem-sucedido via callback");
            } else {
                resultText.setText(result != null ? result : "Nenhum resultado encontrado");
                btnPlayRecognized.setVisibility(View.GONE);

                if (result != null && result.contains("Não reconhecida")) {
                    Toast.makeText(this, "Música não reconhecida. Tente novamente.", Toast.LENGTH_LONG).show();
                }
                Log.w(TAG, " Reconhecimento falhou via callback");
            }
        });
    }

    @Override
    public void updateSongList(List<Song> songs) {
        Log.d(TAG, "updateSongList chamado com " + (songs != null ? songs.size() : 0) + " músicas");
    }

    @Override
    public void updatePlaylistList(List<Playlist> playlists) {
        Log.d(TAG, "updatePlaylistList chamado com " + (playlists != null ? playlists.size() : 0) + " playlists");
    }

    @Override
    public void updateSongInfo(String title, String artist) {
        Log.d(TAG, "updateSongInfo: " + title + " - " + artist);
    }

    @Override
    public void updatePlayPauseIcon(boolean isPlaying) {
        Log.d(TAG, "updatePlayPauseIcon: " + isPlaying);
    }

    @Override
    public void updateProgress(int currentPosition, int duration) {
        // Log muito verboso - comentado para evitar spam
        // Log.d(TAG, "updateProgress: " + currentPosition + "/" + duration);
    }

    @Override
    public void updateArtists(List<String> artists) {
        Log.d(TAG, "updateArtists: " + (artists != null ? artists.size() : 0) + " artistas");
    }

    @Override
    public void requestPermissions() {
        Log.d(TAG, "requestPermissions chamado");
        checkAudioPermission();
    }

    // Métodos opcionais que podem estar na interface MusicView
    // ========================================================

    public void playSong(Song song) {
        Log.d(TAG, "playSong chamado: " + (song != null ? song.getTitle() : "null"));
        presenter.playSong(song);
    }

    public void playPause() {
        Log.d(TAG, "playPause chamado");
        presenter.playPause();
    }

    public void prevSong() {
        Log.d(TAG, "prevSong chamado");
        presenter.prevSong();
    }

    public void nextSong() {
        Log.d(TAG, "nextSong chamado");
        presenter.nextSong();
    }

    public void toggleShuffle() {
        Log.d(TAG, "toggleShuffle chamado");
        presenter.toggleShuffle();
    }

    public void toggleRepeat() {
        Log.d(TAG, "toggleRepeat chamado");
        presenter.toggleRepeat();
    }

    public void seekTo(int progress) {
        Log.d(TAG, "seekTo chamado: " + progress);
        presenter.seekTo(progress);
    }

    public void searchSongs(String query) {
        Log.d(TAG, "searchSongs chamado: " + query);
        presenter.searchSongs(query);
    }

    public void createPlaylist() {
        Log.d(TAG, "createPlaylist chamado");
        presenter.createPlaylist();
    }

    public void recognizeMusic() {
        Log.d(TAG, "recognizeMusic chamado");
        startRecognition();
    }

    public void loadPlaylistSongs(Playlist playlist) {
        Log.d(TAG, "loadPlaylistSongs chamado: " + (playlist != null ? playlist.getName() : "null"));
        presenter.loadPlaylistSongs(playlist);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // RECARREGAR CORES DINÂMICAS AO VOLTAR PARA A ACTIVITY
        setupShazamButtonColor();
    }



    @Override
    protected void onDestroy() {
        Log.d(TAG, "=== DESTRUINDO ACTIVITY ===");
        super.onDestroy();

        // Parar gravação se estiver ativa
        if (audioRecorder != null && audioRecorder.isRecording()) {
            Log.d(TAG, "Parando gravação ativa...");
            audioRecorder.stopRecording();
        }

        if (presenter != null) {
            presenter.onDestroy();
        }

        handler.removeCallbacksAndMessages(null);
        Log.d(TAG, "=== ACTIVITY DESTRUÍDA ===");
    }
}