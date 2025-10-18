package com.example.musicplayer.utils;

import android.content.Context;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class ACRCloudHelper {
    private static final String TAG = "ACRCloudHelper";

    //  SUAS CREDENCIAIS
    private static final String HOST = "identify-eu-west-1.acrcloud.com";
    private static final String ACCESS_KEY = "d72eae591344758ca13e3373e9d0ee12";
    private static final String ACCESS_SECRET = "BI8AB7PvcBUni0yhBziJm9zH619k4wDmSqK1ATmZ";

    private static final String SIGNATURE_VERSION = "1";
    private static final String DATA_TYPE = "audio";

    public static String recognizeMusic(byte[] audioData, Context context) {
        debugCredentials();

        if (audioData == null || audioData.length == 0) {
            return "❌ Dados de áudio vazios ou muito curtos";
        }

        Log.d(TAG, "Iniciando reconhecimento com " + audioData.length + " bytes de áudio WAV");

        try {
            String method = "POST";
            String httpURL = "/v1/identify";
            String timestamp = String.valueOf(System.currentTimeMillis() / 1000);

            // ✅ CALCULAR SIGNATURE
            String signatureString = method + "\n" + httpURL + "\n" + ACCESS_KEY + "\n" +
                    DATA_TYPE + "\n" + SIGNATURE_VERSION + "\n" + timestamp;

            String signature = calculateSignature(signatureString, ACCESS_SECRET);
            String url = "https://" + HOST + httpURL;
            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
            String lineEnd = "\r\n";
            String twoHyphens = "--";

            // ✅ CORREÇÃO: CONSTRUIR MULTIPART FORM DATA CORRETAMENTE
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            // ✅ Parte 1: Sample (áudio)
            outputStream.write((twoHyphens + boundary + lineEnd).getBytes());
            outputStream.write(("Content-Disposition: form-data; name=\"sample\"; filename=\"audio.wav\"" + lineEnd).getBytes());
            outputStream.write(("Content-Type: audio/wav" + lineEnd + lineEnd).getBytes());
            outputStream.write(audioData);
            outputStream.write(lineEnd.getBytes());

            // ✅ Parte 2: Access Key
            outputStream.write((twoHyphens + boundary + lineEnd).getBytes());
            outputStream.write(("Content-Disposition: form-data; name=\"access_key\"" + lineEnd + lineEnd).getBytes());
            outputStream.write((ACCESS_KEY + lineEnd).getBytes());

            // ✅ Parte 3: Data Type
            outputStream.write((twoHyphens + boundary + lineEnd).getBytes());
            outputStream.write(("Content-Disposition: form-data; name=\"data_type\"" + lineEnd + lineEnd).getBytes());
            outputStream.write((DATA_TYPE + lineEnd).getBytes());

            // ✅ Parte 4: Signature Version
            outputStream.write((twoHyphens + boundary + lineEnd).getBytes());
            outputStream.write(("Content-Disposition: form-data; name=\"signature_version\"" + lineEnd + lineEnd).getBytes());
            outputStream.write((SIGNATURE_VERSION + lineEnd).getBytes());

            // ✅ Parte 5: Signature
            outputStream.write((twoHyphens + boundary + lineEnd).getBytes());
            outputStream.write(("Content-Disposition: form-data; name=\"signature\"" + lineEnd + lineEnd).getBytes());
            outputStream.write((signature + lineEnd).getBytes());

            // ✅ Parte 6: Sample Bytes
            outputStream.write((twoHyphens + boundary + lineEnd).getBytes());
            outputStream.write(("Content-Disposition: form-data; name=\"sample_bytes\"" + lineEnd + lineEnd).getBytes());
            outputStream.write((String.valueOf(audioData.length) + lineEnd).getBytes());

            // ✅ Parte 7: Timestamp
            outputStream.write((twoHyphens + boundary + lineEnd).getBytes());
            outputStream.write(("Content-Disposition: form-data; name=\"timestamp\"" + lineEnd + lineEnd).getBytes());
            outputStream.write((timestamp + lineEnd).getBytes());

            // ✅ Final do boundary
            outputStream.write((twoHyphens + boundary + twoHyphens + lineEnd).getBytes());

            byte[] postData = outputStream.toByteArray();
            outputStream.close();

            Log.d(TAG, "Enviando request de " + postData.length + " bytes para ACRCloud");

            return makeHttpRequest(url, boundary, postData);

        } catch (Exception e) {
            Log.e(TAG, "Erro no recognizeMusic: " + e.getMessage(), e);
            return "❌ Erro: " + e.getMessage();
        }
    }

    private static String calculateSignature(String signatureString, String secret) {
        try {
            javax.crypto.spec.SecretKeySpec signingKey = new javax.crypto.spec.SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1");

            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
            mac.init(signingKey);
            byte[] rawHmac = mac.doFinal(signatureString.getBytes(StandardCharsets.UTF_8));

            return android.util.Base64.encodeToString(rawHmac, android.util.Base64.NO_WRAP);

        } catch (Exception e) {
            Log.e(TAG, "Erro ao calcular signature: " + e.getMessage());
            return "";
        }
    }

    private static String makeHttpRequest(String urlString, String boundary, byte[] postData) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();

            // ✅ CONFIGURAÇÃO CORRIGIDA DA CONEXÃO
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            connection.setRequestProperty("User-Agent", "ACRCloud Android SDK");
            connection.setRequestProperty("Connection", "Keep-Alive");
            connection.setConnectTimeout(30000); // 30 segundos
            connection.setReadTimeout(30000); // 30 segundos
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setUseCaches(false);

            // ✅ IMPORTANTE: Definir Content-Length
            connection.setRequestProperty("Content-Length", String.valueOf(postData.length));
            connection.setFixedLengthStreamingMode(postData.length);

            Log.d(TAG, "Conectando com ACRCloud... Headers configurados");

            // ✅ ENVIAR DADOS
            try (OutputStream os = connection.getOutputStream()) {
                os.write(postData, 0, postData.length);
                os.flush();
            }

            Log.d(TAG, "Dados enviados, aguardando resposta...");

            // ✅ VERIFICAR RESPOSTA
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "Código de resposta HTTP: " + responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                Log.d(TAG, "Resposta bruta do ACRCloud: " + response.toString());
                return parseResponse(response.toString());

            } else {
                // ✅ LER MENSAGEM DE ERRO
                String errorMessage = "Erro HTTP: " + responseCode;
                try {
                    BufferedReader errorReader = new BufferedReader(
                            new InputStreamReader(connection.getErrorStream()));
                    String errorLine;
                    StringBuilder errorResponse = new StringBuilder();
                    while ((errorLine = errorReader.readLine()) != null) {
                        errorResponse.append(errorLine);
                    }
                    errorReader.close();
                    errorMessage += " - " + errorResponse.toString();
                    Log.e(TAG, "Resposta de erro: " + errorResponse.toString());
                } catch (Exception e) {
                    Log.e(TAG, "Não foi possível ler stream de erro: " + e.getMessage());
                }

                return "❌ " + errorMessage;
            }

        } catch (Exception e) {
            Log.e(TAG, "Erro na requisição HTTP: " + e.getMessage(), e);
            return "❌ Erro de conexão: " + e.getMessage();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String parseResponse(String response) {
        try {
            Log.d(TAG, "Processando resposta JSON...");
            JSONObject json = new JSONObject(response);
            JSONObject status = json.getJSONObject("status");
            int code = status.getInt("code");
            String msg = status.getString("msg");

            Log.d(TAG, "Status ACRCloud - Código: " + code + ", Mensagem: " + msg);

            // ✅ VERIFICAR CÓDIGOS DE ERRO COMUNS
            if (code == 3000) {
                return "❌ Erro 3000: Serviço indisponível";
            } else if (code == 3001) {
                return "❌ Erro 3001: Parâmetros inválidos";
            } else if (code == 3014) {
                return "❌ Erro 3014: Credenciais inválidas - verifique Access Key e Secret";
            } else if (code == 3015) {
                return "❌ Erro 3015: Quota excedida";
            } else if (code != 0) {
                return "❌ Erro " + code + ": " + msg;
            }

            // ✅ PROCESSAR RESULTADO BEM-SUCEDIDO
            if (json.has("metadata")) {
                JSONObject metadata = json.getJSONObject("metadata");

                if (metadata.has("music") && metadata.getJSONArray("music").length() > 0) {
                    JSONArray musicArray = metadata.getJSONArray("music");
                    JSONObject music = musicArray.getJSONObject(0);

                    String title = music.getString("title");
                    String artist = "Artista Desconhecido";

                    if (music.has("artists") && music.getJSONArray("artists").length() > 0) {
                        artist = music.getJSONArray("artists").getJSONObject(0).getString("name");
                    }

                    String album = "";
                    if (music.has("album") && !music.isNull("album")) {
                        JSONObject albumObj = music.getJSONObject("album");
                        album = albumObj.getString("name");
                    }

                    StringBuilder result = new StringBuilder();
                    result.append("✅ Música Reconhecida!\n\n");
                    result.append("🎵 Título: ").append(title).append("\n");
                    result.append("🎤 Artista: ").append(artist).append("\n");

                    if (!album.isEmpty()) {
                        result.append("💿 Álbum: ").append(album).append("\n");
                    }

                    result.append("\nClique em 'Tocar Música'!");

                    Log.d(TAG, "Música reconhecida: " + title + " - " + artist);
                    return result.toString();
                }
            }

            return " Nenhuma música reconhecida no áudio";

        } catch (Exception e) {
            Log.e(TAG, "Erro ao processar resposta JSON: " + e.getMessage(), e);
            return " Erro ao processar resposta: " + e.getMessage();
        }
    }

    private static void debugCredentials() {
        Log.d(TAG, "=== DEBUG ACRCLOUD ===");
        Log.d(TAG, "Host: " + HOST);
        Log.d(TAG, "Access Key: " + ACCESS_KEY);
        Log.d(TAG, "Access Secret: " + (ACCESS_SECRET != null ?
                ACCESS_SECRET.substring(0, Math.min(8, ACCESS_SECRET.length())) + "..." : "null"));
        Log.d(TAG, "======================");
    }
}