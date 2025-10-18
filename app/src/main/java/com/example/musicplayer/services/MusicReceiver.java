package com.example.musicplayer.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class MusicReceiver extends BroadcastReceiver {
    private static final String TAG = "MusicReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Broadcast recebido: " + action);

        if (action == null) return;

        Intent serviceIntent = new Intent(context, MusicService.class);
        serviceIntent.setAction(action);

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }

            Log.d(TAG, "Acao encaminhada para MusicService: " + action);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao encaminhar acao para servico: " + e.getMessage());
        }
    }
}