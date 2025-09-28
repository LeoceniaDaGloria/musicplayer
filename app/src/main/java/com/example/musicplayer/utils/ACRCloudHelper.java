/*package com.example.musicplayer.utils;

import com.acrcloud.ACRCloudConfig;
import com.acrcloud.ACRCloudClient;

public class ACRCloudHelper {
    private static final String HOST = "identify-us-west-1.acrcloud.com"; // Substitua pelo seu host
    private static final String ACCESS_KEY = "34d2893b50140cc35bee93331474e9f4"; // Substitua
    private static final String ACCESS_SECRET = "Vp2yGtShgu9rEUmv6M6xE96tlAvRPHSoronasSmH"; // Substitua

    private static ACRCloudClient mClient;
    private static boolean initState;

    public static ACRCloudClient getClient() {
        if (mClient == null || !initState) {
            ACRCloudConfig mConfig = new ACRCloudConfig();
            mConfig.host = HOST;
            mConfig.accessKey = ACCESS_KEY;
            mConfig.accessSecret = ACCESS_SECRET;

            // Configurações opcionais (baseado no exemplo)
            mConfig.recorderConfig.isVolumeCallback = true;
            mConfig.recorderConfig.reservedRecordBufferMS = 3000; // 3 segundos de pré-gravação

            mClient = new ACRCloudClient();
            initState = mClient.initWithConfig(mConfig);
            if (!initState) {
                // Log ou tratamento de erro (opcional)
                android.util.Log.e("ACRCloudHelper", "Init failed");
            }
        }
        return mClient;
    }

    // Método para liberar recursos (opcional, baseado em onDestroy)
    public static void release() {
        if (mClient != null) {
            mClient.release();
            initState = false;
            mClient = null;
        }
    }
}

*/