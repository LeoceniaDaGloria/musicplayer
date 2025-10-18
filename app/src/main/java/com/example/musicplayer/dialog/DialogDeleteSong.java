package com.example.musicplayer.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.example.musicplayer.R;
import com.example.musicplayer.model.Song;
import com.example.musicplayer.presenter.MusicPresenter;

import java.io.File;

public class DialogDeleteSong extends Dialog {
    private static final String TAG = "DialogDeleteSong";
    private final Context context;
    private final MusicPresenter presenter;
    private final Song song;
    private TextView tvWarning;
    private Button btnDelete, btnCancel;

    public DialogDeleteSong(@NonNull Context context, MusicPresenter presenter, Song song) {
        super(context, R.style.MyCustomDialog);
        this.context = context;
        this.presenter = presenter;
        this.song = song;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_delete_song);

        getWindow().setLayout(
                (int) (getScreenWidth() * 0.85),
                android.view.WindowManager.LayoutParams.WRAP_CONTENT
        );

        setTitle("Excluir Música");
        setCancelable(true);

        initializeViews();
        setupListeners();
        setupWarningText();

        Log.d(TAG, "DialogDeleteSong criado para: " + song.getTitle());
    }

    private int getScreenWidth() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        ((Activity) context).getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        return displayMetrics.widthPixels;
    }

    private void initializeViews() {
        tvWarning = findViewById(R.id.tv_warning);
        btnDelete = findViewById(R.id.btn_delete);
        btnCancel = findViewById(R.id.btn_cancel);
    }

    private void setupListeners() {
        btnCancel.setOnClickListener(v -> {
            Log.d(TAG, "Cancelar clicado");
            dismiss();
        });

        btnDelete.setOnClickListener(v -> {
            Log.d(TAG, "Excluir clicado - mostrando confirmação final");
            showFinalConfirmation();
        });
    }

    private void setupWarningText() {
        if (song != null) {
            String warning = String.format(
                    "Tem certeza que deseja excluir \"%s\"?\n\nEsta ação não pode ser desfeita.",
                    song.getTitle()
            );
            tvWarning.setText(warning);
            Log.d(TAG, "Texto de aviso configurado para: " + song.getTitle());
        }
    }

    private void showFinalConfirmation() {
        Log.d(TAG, "Mostrando diálogo de confirmação final");

        new AlertDialog.Builder(context, R.style.MyAlertDialogTheme)
                .setTitle("Confirmação Final")
                .setMessage("ATENÇÃO: A música será permanentemente excluída do seu dispositivo. Continuar?")
                .setPositiveButton("EXCLUIR", (dialog, which) -> {
                    Log.d(TAG, "Usuário confirmou exclusão");
                    deleteSong();
                })
                .setNegativeButton("Cancelar", (dialog, which) -> {
                    Log.d(TAG, "Usuário cancelou exclusão");
                    dialog.dismiss();
                })
                .show();
    }

    private void deleteSong() {
        try {
            Log.d(TAG, "=== INICIANDO PROCESSO DE EXCLUSÃO ===");
            Log.d(TAG, "Música: " + song.getTitle());
            Log.d(TAG, "Caminho: " + song.getPath());

            // Verificação adicional antes de deletar
            File file = new File(song.getPath());
            boolean fileExists = file.exists();
            boolean isWritable = file.canWrite();

            Log.d(TAG, "File path: " + song.getPath());
            Log.d(TAG, "File exists: " + fileExists);
            Log.d(TAG, "File is writable: " + isWritable);

            if (!fileExists) {
                Toast.makeText(context, "Arquivo não encontrado", Toast.LENGTH_LONG).show();
                Log.e(TAG, "Arquivo não existe");
                return;
            }

            if (!isWritable) {
                Toast.makeText(context, "Sem permissão para excluir o arquivo", Toast.LENGTH_LONG).show();
                Log.e(TAG, "Sem permissão de escrita");
                return;
            }

            if (presenter == null) {
                Log.e(TAG, "Presenter é nulo - não é possível excluir");
                Toast.makeText(context, "Erro: Presenter não disponível", Toast.LENGTH_LONG).show();
                return;
            }

            boolean success = presenter.deleteSong(song);

            Log.d(TAG, "Resultado da exclusão: " + success);

            if (success) {
                Toast.makeText(context, "Música excluída com sucesso", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Música excluída com sucesso - fechando diálogo");
                dismiss();
            } else {
                Toast.makeText(context, "Erro ao excluir música. Verifique as permissões.", Toast.LENGTH_LONG).show();
                Log.e(TAG, "Falha na exclusão da música");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deleting song: " + e.getMessage(), e);
            Toast.makeText(context, "Erro: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}