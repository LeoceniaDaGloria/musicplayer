package com.example.musicplayer.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.example.musicplayer.R;
import com.example.musicplayer.model.Song;
import com.example.musicplayer.presenter.MusicPresenter;
import com.example.musicplayer.utils.FileUtils;

import java.io.File;

public class DialogRenameSong extends Dialog {
    private static final String TAG = "DialogRenameSong";
    private final Context context;
    private final MusicPresenter presenter;
    private final Song song;
    private EditText etNewName;
    private TextView tvCurrentName;
    private Button btnRename, btnCancel;

    public DialogRenameSong(@NonNull Context context, MusicPresenter presenter, Song song) {
        super(context, R.style.MyCustomDialog);
        this.context = context;
        this.presenter = presenter;
        this.song = song;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_rename_song);

        getWindow().setLayout(
                (int) (getScreenWidth() * 0.85),
                android.view.WindowManager.LayoutParams.WRAP_CONTENT
        );

        setTitle("Renomear Música");
        setCancelable(true);

        initializeViews();
        setupListeners();
        populateCurrentName();

        Log.d(TAG, "DialogRenameSong criado para: " + song.getTitle());
    }

    private int getScreenWidth() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        ((Activity) context).getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        return displayMetrics.widthPixels;
    }

    private void initializeViews() {
        tvCurrentName = findViewById(R.id.tv_current_name);
        etNewName = findViewById(R.id.et_new_name);
        btnRename = findViewById(R.id.btn_rename);
        btnCancel = findViewById(R.id.btn_cancel);
    }

    private void setupListeners() {
        btnCancel.setOnClickListener(v -> {
            Log.d(TAG, "Cancelar clicado");
            dismiss();
        });

        btnRename.setOnClickListener(v -> {
            Log.d(TAG, "Renomear clicado");
            attemptRename();
        });

        etNewName.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                String currentName = FileUtils.getFileNameWithoutExtension(song.getTitle());
                etNewName.setSelection(0, currentName.length());
                Log.d(TAG, "Campo de texto focado - selecionado: " + currentName);
            }
        });
    }

    private void populateCurrentName() {
        if (song != null) {
            tvCurrentName.setText(song.getTitle());
            String currentNameWithoutExt = FileUtils.getFileNameWithoutExtension(song.getTitle());
            etNewName.setText(currentNameWithoutExt);
            etNewName.requestFocus();

            Log.d(TAG, "Nome atual preenchido: " + song.getTitle());
            Log.d(TAG, "Nome sem extensão: " + currentNameWithoutExt);
        }
    }

    private void attemptRename() {
        String newName = etNewName.getText().toString().trim();

        Log.d(TAG, "=== TENTATIVA DE RENOMEAR ===");
        Log.d(TAG, "Nome atual: " + song.getTitle());
        Log.d(TAG, "Novo nome: " + newName);
        Log.d(TAG, "Caminho: " + song.getPath());

        if (TextUtils.isEmpty(newName)) {
            etNewName.setError("Digite um nome para a música");
            Log.w(TAG, "Nome vazio - validação falhou");
            return;
        }

        // Verifica caracteres inválidos
        if (newName.contains("/") || newName.contains("\\") || newName.contains(":") ||
                newName.contains("*") || newName.contains("?") || newName.contains("\"") ||
                newName.contains("<") || newName.contains(">") || newName.contains("|")) {
            etNewName.setError("Nome contém caracteres inválidos");
            Log.w(TAG, "Caracteres inválidos no nome");
            return;
        }

        String currentNameWithoutExt = FileUtils.getFileNameWithoutExtension(song.getTitle());
        if (newName.equals(currentNameWithoutExt)) {
            Toast.makeText(context, "O nome é igual ao atual", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "Nome igual ao atual");
            return;
        }

        // Adiciona a extensão do arquivo original
        String fileExtension = FileUtils.getFileExtension(song.getTitle());
        String newFileName = newName + "." + fileExtension;

        Log.d(TAG, "Extensão do arquivo: " + fileExtension);
        Log.d(TAG, "Novo nome completo: " + newFileName);

        // Verifica se o novo arquivo já existe
        File originalFile = new File(song.getPath());
        File newFile = new File(originalFile.getParent(), newFileName);

        Log.d(TAG, "Arquivo original existe: " + originalFile.exists());
        Log.d(TAG, "Arquivo original pode escrever: " + originalFile.canWrite());
        Log.d(TAG, "Novo arquivo já existe: " + newFile.exists());

        if (newFile.exists()) {
            etNewName.setError("Já existe um arquivo com este nome");
            Log.w(TAG, "Arquivo com novo nome já existe");
            return;
        }

        renameSong(newFileName);
    }

    private void renameSong(String newFileName) {
        try {
            Log.d(TAG, "=== INICIANDO PROCESSO DE RENOMEAR ===");
            Log.d(TAG, "Original: " + song.getTitle());
            Log.d(TAG, "New name: " + newFileName);
            Log.d(TAG, "Full path: " + song.getPath());

            if (presenter == null) {
                Log.e(TAG, "Presenter é nulo - não é possível renomear");
                Toast.makeText(context, "Erro: Presenter não disponível", Toast.LENGTH_LONG).show();
                return;
            }

            boolean success = presenter.renameSong(song, newFileName);

            Log.d(TAG, "Resultado do rename: " + success);

            if (success) {
                Toast.makeText(context, "Música renomeada com sucesso", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Música renomeada com sucesso - fechando diálogo");
                dismiss();
            } else {
                Toast.makeText(context, "Erro ao renomear música. Verifique as permissões.", Toast.LENGTH_LONG).show();
                Log.e(TAG, "Falha ao renomear arquivo");
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro durante renomeação: " + e.getMessage(), e);
            Toast.makeText(context, "Erro: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}