// DialogChangeCover.java
package com.example.musicplayer.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.example.musicplayer.R;
import com.example.musicplayer.model.Song;
import com.example.musicplayer.presenter.MusicPresenter;
import com.example.musicplayer.utils.ImageUtils;

import java.io.File;

public class DialogChangeCover extends Dialog {
    private static final int PICK_IMAGE_REQUEST = 1001;
    private static final int TAKE_PHOTO_REQUEST = 1002;

    private final Context context;
    private final MusicPresenter presenter;
    private final Song song;
    private ImageView ivCurrentCover;
    private Button btnGallery, btnCamera, btnRemove, btnCancel;

    // Variáveis para controle da câmera
    private String currentPhotoPath;

    // ============================================================
    // INTERFACE PARA CALLBACK - ADICIONADA
    // ============================================================

    public interface OnCoverChangeListener {
        void onCoverChanged();
        void onCoverChangeError(String error);
    }

    private OnCoverChangeListener coverChangeListener;

    public void setOnCoverChangeListener(OnCoverChangeListener listener) {
        this.coverChangeListener = listener;
    }

    public DialogChangeCover(@NonNull Context context, MusicPresenter presenter, Song song) {
        super(context, R.style.MyCustomDialog);
        this.context = context;
        this.presenter = presenter;
        this.song = song;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_change_cover);

        getWindow().setLayout(
                (int) (getScreenWidth() * 0.9),
                android.view.WindowManager.LayoutParams.WRAP_CONTENT
        );

        setTitle("Alterar Capa da Música");
        setCancelable(true);

        initializeViews();
        setupListeners();
        loadCurrentCover();
    }

    private int getScreenWidth() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        ((Activity) context).getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        return displayMetrics.widthPixels;
    }

    private void initializeViews() {
        ivCurrentCover = findViewById(R.id.iv_current_cover);
        btnGallery = findViewById(R.id.btn_gallery);
        btnCamera = findViewById(R.id.btn_camera);
        btnRemove = findViewById(R.id.btn_remove);
        btnCancel = findViewById(R.id.btn_cancel);
    }

    private void setupListeners() {
        btnCancel.setOnClickListener(v -> dismiss());

        btnGallery.setOnClickListener(v -> pickImageFromGallery());

        btnCamera.setOnClickListener(v -> takePhoto());

        btnRemove.setOnClickListener(v -> removeCover());
    }

    private void loadCurrentCover() {
        if (song != null) {
            String coverPath = presenter.getSongCoverPath(song.getId());
            if (coverPath != null && !coverPath.isEmpty()) {
                Log.d("DialogChangeCover", "Loading custom cover: " + coverPath);
                Glide.with(context)
                        .load(new File(coverPath))
                        .placeholder(R.drawable.ic_lm_capa_placeholder)
                        .into(ivCurrentCover);
            } else {
                Log.d("DialogChangeCover", "Loading album art from file");
                Uri songUri = Uri.parse(song.getPath());
                ImageUtils.loadAlbumArt(context, songUri, ivCurrentCover);
            }
        }
    }

    public void pickImageFromGallery() {
        Log.d("DialogChangeCover", "pickImageFromGallery chamado");

        // VERIFICAR PERMISSÃO ANTES de abrir a galeria
        boolean hasPermission = presenter.hasGalleryPermission();
        Log.d("DialogChangeCover", "Tem permissão de galeria: " + hasPermission);

        if (!hasPermission) {
            Log.d("DialogChangeCover", "Solicitando permissão...");
            Toast.makeText(context, "Solicitando permissão para acessar fotos...", Toast.LENGTH_SHORT).show();
            presenter.requestGalleryPermission();
            dismiss();
            return;
        }

        Log.d("DialogChangeCover", "Abrindo galeria...");

        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");

        if (intent.resolveActivity(context.getPackageManager()) != null) {
            ((Activity) context).startActivityForResult(intent, PICK_IMAGE_REQUEST);
        } else {
            Toast.makeText(context, "Nenhum app de galeria encontrado", Toast.LENGTH_SHORT).show();
        }

        dismiss();
    }

    private void takePhoto() {
        try {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

            // Cria um arquivo temporário para a foto usando ImageUtils
            File photoFile = ImageUtils.createImageFile(context);
            if (photoFile != null) {
                currentPhotoPath = photoFile.getAbsolutePath();

                Uri photoUri = FileProvider.getUriForFile(context,
                        context.getPackageName() + ".provider",
                        photoFile);

                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);

                if (intent.resolveActivity(context.getPackageManager()) != null) {
                    ((Activity) context).startActivityForResult(intent, TAKE_PHOTO_REQUEST);
                } else {
                    Toast.makeText(context, "Nenhum app de câmera encontrado", Toast.LENGTH_SHORT).show();
                    cleanupTempFile();
                }
            } else {
                Toast.makeText(context, "Erro ao criar arquivo para foto", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e("DialogChangeCover", "Error taking photo: " + e.getMessage());
            Toast.makeText(context, "Erro ao acessar câmera", Toast.LENGTH_SHORT).show();
            cleanupTempFile();
        }

        dismiss();
    }

    private void removeCover() {
        new AlertDialog.Builder(context, R.style.MyAlertDialogTheme)
                .setTitle("Remover Capa")
                .setMessage("Deseja remover a capa personalizada desta música?")
                .setPositiveButton("Remover", (dialog, which) -> removeCurrentCover())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ============================================================
    // MÉTODOS DE PROCESSAMENTO COM VERIFICAÇÃO DE PERMISSÃO
    // ============================================================

    /**
     * Processa a seleção de imagem da galeria
     */
    public void handleGalleryResult(Intent data) {
        try {
            if (data != null && data.getData() != null) {
                Uri selectedImageUri = data.getData();

                // Converte URI para caminho real
                String imagePath = getRealPathFromURI(selectedImageUri);
                if (imagePath != null) {
                    // Atualiza capa usando o presenter
                    boolean success = presenter.updateSongCover(song.getId(), imagePath);

                    if (success) {
                        Toast.makeText(context, "Capa atualizada com sucesso!", Toast.LENGTH_SHORT).show();
                        loadCurrentCover();

                        // NOTIFICA SUCESSO - ADICIONADO
                        if (coverChangeListener != null) {
                            coverChangeListener.onCoverChanged();
                        }
                    } else {
                        String error = "Erro ao atualizar capa. Verifique as permissões.";
                        Toast.makeText(context, error, Toast.LENGTH_LONG).show();

                        // NOTIFICA ERRO - ADICIONADO
                        if (coverChangeListener != null) {
                            coverChangeListener.onCoverChangeError(error);
                        }
                    }
                } else {
                    String error = "Erro ao acessar a imagem selecionada";
                    Toast.makeText(context, error, Toast.LENGTH_SHORT).show();

                    // NOTIFICA ERRO - ADICIONADO
                    if (coverChangeListener != null) {
                        coverChangeListener.onCoverChangeError(error);
                    }
                }
            }
        } catch (Exception e) {
            Log.e("DialogChangeCover", "Erro ao processar imagem da galeria: " + e.getMessage());
            String error = "Erro ao processar imagem: " + e.getMessage();
            Toast.makeText(context, "Erro ao processar imagem", Toast.LENGTH_SHORT).show();

            // NOTIFICA ERRO - ADICIONADO
            if (coverChangeListener != null) {
                coverChangeListener.onCoverChangeError(error);
            }
        }
    }

    /**
     * Processa a foto da câmera
     */
    public void handleCameraResult() {
        try {
            if (currentPhotoPath != null) {
                // Atualiza capa usando o presenter
                boolean success = presenter.updateSongCover(song.getId(), currentPhotoPath);

                if (success) {
                    Toast.makeText(context, "Capa atualizada com sucesso!", Toast.LENGTH_SHORT).show();
                    loadCurrentCover();

                    // NOTIFICA SUCESSO - ADICIONADO
                    if (coverChangeListener != null) {
                        coverChangeListener.onCoverChanged();
                    }
                } else {
                    String error = "Erro ao atualizar capa. Verifique as permissões.";
                    Toast.makeText(context, error, Toast.LENGTH_LONG).show();

                    // Limpa arquivo temporário em caso de erro
                    cleanupTempFile();

                    // NOTIFICA ERRO - ADICIONADO
                    if (coverChangeListener != null) {
                        coverChangeListener.onCoverChangeError(error);
                    }
                }
            }
        } catch (Exception e) {
            Log.e("DialogChangeCover", "Erro ao processar foto da câmera: " + e.getMessage());
            String error = "Erro ao processar foto: " + e.getMessage();
            Toast.makeText(context, "Erro ao processar foto", Toast.LENGTH_SHORT).show();
            cleanupTempFile();

            // NOTIFICA ERRO - ADICIONADO
            if (coverChangeListener != null) {
                coverChangeListener.onCoverChangeError(error);
            }
        }
    }

    /**
     * Remove capa existente
     */
    private void removeCurrentCover() {
        try {
            boolean success = presenter.removeSongCover(song.getId());

            if (success) {
                Toast.makeText(context, "Capa removida com sucesso!", Toast.LENGTH_SHORT).show();
                loadCurrentCover();

                // NOTIFICA SUCESSO - ADICIONADO
                if (coverChangeListener != null) {
                    coverChangeListener.onCoverChanged();
                }
            } else {
                String error = "Erro ao remover capa";
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show();

                // NOTIFICA ERRO - ADICIONADO
                if (coverChangeListener != null) {
                    coverChangeListener.onCoverChangeError(error);
                }
            }
        } catch (Exception e) {
            Log.e("DialogChangeCover", "Erro ao remover capa: " + e.getMessage());
            String error = "Erro ao remover capa: " + e.getMessage();
            Toast.makeText(context, "Erro ao remover capa", Toast.LENGTH_SHORT).show();

            // NOTIFICA ERRO - ADICIONADO
            if (coverChangeListener != null) {
                coverChangeListener.onCoverChangeError(error);
            }
        }
    }

    /**
     * Obtém caminho real a partir de URI
     */
    private String getRealPathFromURI(Uri contentUri) {
        try {
            String[] proj = { android.provider.MediaStore.Images.Media.DATA };
            android.database.Cursor cursor = context.getContentResolver().query(contentUri, proj, null, null, null);

            if (cursor != null) {
                int column_index = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DATA);
                cursor.moveToFirst();
                String path = cursor.getString(column_index);
                cursor.close();
                return path;
            }
            return null;
        } catch (Exception e) {
            Log.e("DialogChangeCover", "Erro ao obter caminho real da URI: " + e.getMessage());
            return null;
        }
    }

    /**
     * Limpa arquivo temporário da câmera
     */
    private void cleanupTempFile() {
        try {
            if (currentPhotoPath != null) {
                File tempFile = new File(currentPhotoPath);
                if (tempFile.exists()) {
                    boolean deleted = tempFile.delete();
                    Log.d("DialogChangeCover", "Arquivo temporário limpo: " + deleted);
                }
                currentPhotoPath = null;
            }
        } catch (Exception e) {
            Log.e("DialogChangeCover", "Erro ao limpar arquivo temporário: " + e.getMessage());
        }
    }


    public void onImageSelected(Uri imageUri) {
        try {
            Log.d("DialogChangeCover", "Image selected: " + imageUri.toString());

            if (presenter.updateSongCover(song.getId(), imageUri.toString())) {
                Toast.makeText(context, "Capa alterada com sucesso", Toast.LENGTH_SHORT).show();
                loadCurrentCover();

                // NOTIFICA SUCESSO - ADICIONADO
                if (coverChangeListener != null) {
                    coverChangeListener.onCoverChanged();
                }
            } else {
                Toast.makeText(context, "Erro ao alterar capa", Toast.LENGTH_SHORT).show();

                // NOTIFICA ERRO - ADICIONADO
                if (coverChangeListener != null) {
                    coverChangeListener.onCoverChangeError("Erro ao alterar capa");
                }
            }
        } catch (Exception e) {
            Log.e("DialogChangeCover", "Error setting cover: " + e.getMessage());
            Toast.makeText(context, "Erro: " + e.getMessage(), Toast.LENGTH_LONG).show();

            // NOTIFICA ERRO - ADICIONADO
            if (coverChangeListener != null) {
                coverChangeListener.onCoverChangeError(e.getMessage());
            }
        }
    }

    public void onPhotoTaken(android.graphics.Bitmap photo) {
        try {
            String imagePath = ImageUtils.saveBitmapToStorage(context, photo, "cover_" + song.getId());
            Log.d("DialogChangeCover", "Photo saved to: " + imagePath);

            if (imagePath != null && presenter.updateSongCover(song.getId(), imagePath)) {
                Toast.makeText(context, "Capa alterada com sucesso", Toast.LENGTH_SHORT).show();
                loadCurrentCover();

                // NOTIFICA SUCESSO - ADICIONADO
                if (coverChangeListener != null) {
                    coverChangeListener.onCoverChanged();
                }
            } else {
                Toast.makeText(context, "Erro ao salvar capa", Toast.LENGTH_SHORT).show();

                // NOTIFICA ERRO - ADICIONADO
                if (coverChangeListener != null) {
                    coverChangeListener.onCoverChangeError("Erro ao salvar capa");
                }
            }
        } catch (Exception e) {
            Log.e("DialogChangeCover", "Error saving photo: " + e.getMessage());
            Toast.makeText(context, "Erro: " + e.getMessage(), Toast.LENGTH_LONG).show();

            // NOTIFICA ERRO - ADICIONADO
            if (coverChangeListener != null) {
                coverChangeListener.onCoverChangeError(e.getMessage());
            }
        }
    }

    // ============================================================
    // MÉTODO PARA LIMPEZA AO DESTRUIR
    // ============================================================

    @Override
    public void dismiss() {
        cleanupTempFile();
        super.dismiss();
    }
}