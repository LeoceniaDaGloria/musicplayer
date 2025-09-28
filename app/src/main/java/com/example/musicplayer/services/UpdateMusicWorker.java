package com.example.musicplayer.services;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.musicplayer.db.MusicRepository;

/**
 * Worker para atualizar lista de músicas em background via WorkManager.
 * Executa periodicamente para sincronizar com MediaStore.
 */
public class UpdateMusicWorker extends Worker {
    private static final String TAG = "UpdateMusicWorker";

    /**
     * Construtor do Worker.
     * @param context Contexto.
     * @param params Parâmetros.
     */
    public UpdateMusicWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Executa a atualização de músicas
        try {
            MusicRepository repository = new MusicRepository(getApplicationContext());
            repository.loadSongsFromDevice(getApplicationContext());
            Log.d(TAG, "Music update completed in background");
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Error updating music: " + e.getMessage());
            return Result.retry();
        }
    }
}