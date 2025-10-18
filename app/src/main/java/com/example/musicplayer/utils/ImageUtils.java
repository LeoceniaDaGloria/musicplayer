package com.example.musicplayer.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.Log;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.example.musicplayer.R;  // Import do R

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ImageUtils {

    public static void loadAlbumArt(Context context, Uri songUri, ImageView imageView) {
        try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
            retriever.setDataSource(context, songUri);
            byte[] art = retriever.getEmbeddedPicture();
            if (art != null) {
                Bitmap bitmap = BitmapFactory.decodeByteArray(art, 0, art.length);
                Glide.with(context)
                        .load(bitmap)
                        .into(imageView);
            } else {
                imageView.setImageResource(R.drawable.ic_lm_capa_placeholder);
            }
        } catch (Exception e) {
            imageView.setImageResource(R.drawable.ic_lm_capa_placeholder);
        }
    }

    public static String saveBitmapToStorage(Context context, Bitmap bitmap, String fileName) {
        try {
            File storageDir = context.getExternalFilesDir("covers");
            if (!storageDir.exists()) {
                storageDir.mkdirs();
            }

            File imageFile = new File(storageDir, fileName + ".jpg");
            FileOutputStream out = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.flush();
            out.close();

            return imageFile.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Bitmap getAlbumArt(Context context, Uri songUri) {
        try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
            retriever.setDataSource(context, songUri);
            byte[] art = retriever.getEmbeddedPicture();
            if (art != null) {
                return BitmapFactory.decodeByteArray(art, 0, art.length);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // ============================================================
    // NOVO MÉTODO: createImageFile - PARA CRIAR ARQUIVO TEMPORÁRIO DA CÂMERA
    // ============================================================

    /**
     * Cria um arquivo de imagem temporário para a câmera
     */
    public static File createImageFile(Context context) throws IOException {
        // Cria um nome de arquivo único com timestamp
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "COVER_" + timeStamp + "_";

        // Diretório para armazenar as fotos temporárias
        File storageDir = context.getExternalFilesDir("temp_covers");
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }

        // Cria o arquivo
        File imageFile = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        return imageFile;
    }

    // ============================================================
    // MÉTODO ADICIONAL: cleanupTempFiles - PARA LIMPEZA DE ARQUIVOS TEMPORÁRIOS
    // ============================================================

    /**
     * Limpa arquivos temporários antigos (mais de 1 hora)
     */
    public static void cleanupTempFiles(Context context) {
        try {
            File storageDir = context.getExternalFilesDir("temp_covers");
            if (storageDir != null && storageDir.exists()) {
                File[] files = storageDir.listFiles();
                if (files != null) {
                    long currentTime = System.currentTimeMillis();
                    long oneHourAgo = currentTime - (60 * 60 * 1000); // 1 hora em milissegundos

                    for (File file : files) {
                        if (file.lastModified() < oneHourAgo) {
                            boolean deleted = file.delete();
                            Log.d("ImageUtils", "Arquivo temporário limpo: " + file.getName() + " - " + deleted);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e("ImageUtils", "Erro ao limpar arquivos temporários: " + e.getMessage());
        }
    }
}