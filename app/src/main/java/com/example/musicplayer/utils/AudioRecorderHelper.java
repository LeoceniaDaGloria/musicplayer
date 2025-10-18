package com.example.musicplayer.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;
import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Helper para gravação de áudio para reconhecimento ACRCloud
 */
public class AudioRecorderHelper {
    private static final String TAG = "AudioRecorderHelper";

    // ✅ CONFIGURAÇÃO OTIMIZADA PARA ACRCLOUD
    private static final int SAMPLE_RATE = 16000; // ACRCloud funciona melhor com 16kHz
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int RECORDING_DURATION_MS = 8000; // 8 segundos (suficiente para ACRCloud)

    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private Thread recordingThread;
    private Context context;

    public interface RecordingCallback {
        void onRecordingComplete(byte[] audioData);
        void onRecordingError(String error);
    }

    public AudioRecorderHelper(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * ✅ VERIFICAÇÃO EXPLÍCITA DE PERMISSÃO - CORRIGIDO
     */
    public boolean hasAudioPermission() {
        int permissionStatus = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO);
        boolean hasPermission = permissionStatus == PackageManager.PERMISSION_GRANTED;
        Log.d(TAG, "Verificação de permissão: " + hasPermission);
        return hasPermission;
    }

    /**
     * ✅ MÉTODO PRINCIPAL CORRIGIDO COM VERIFICAÇÃO EXPLÍCITA
     */
    public void startRecording(RecordingCallback callback) {
        // VERIFICAÇÃO EXPLÍCITA E SEGURA DA PERMISSÃO
        if (!hasAudioPermission()) {
            Log.w(TAG, "Permissão RECORD_AUDIO negada - não é possível gravar áudio");
            callback.onRecordingError("Permissão de gravação de áudio não concedida");
            return;
        }

        if (isRecording) {
            callback.onRecordingError("Já está gravando");
            return;
        }

        try {
            int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                callback.onRecordingError("Buffer size inválido: " + bufferSize);
                return;
            }

            // ✅ VERIFICAR SE O DISPOSITIVO SUPORTA A CONFIGURAÇÃO
            if (!isConfigurationSupported()) {
                callback.onRecordingError("Configuração de áudio não suportada pelo dispositivo");
                return;
            }

            // ✅ VERIFICAÇÃO EXPLÍCITA DE PERMISSÃO ANTES DE CRIAR AudioRecord
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                throw new SecurityException("Permissão RECORD_AUDIO não concedida");
            }

            // ✅ CRIAÇÃO DO AudioRecord COM TRY-CATCH ESPECÍFICO
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize * 2 // Buffer maior para evitar underrun
            );

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                callback.onRecordingError("AudioRecord não inicializado - Estado: " + audioRecord.getState());
                if (audioRecord != null) {
                    audioRecord.release();
                    audioRecord = null;
                }
                return;
            }

            // ✅ INICIAR GRAVAÇÃO COM VERIFICAÇÃO DE SEGURANÇA
            try {
                audioRecord.startRecording();
                isRecording = true;
                Log.d(TAG, "Gravação iniciada com sucesso");
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException ao iniciar gravação: " + e.getMessage());
                callback.onRecordingError("Erro de segurança ao iniciar gravação");
                if (audioRecord != null) {
                    audioRecord.release();
                    audioRecord = null;
                }
                return;
            }

            // ✅ CALCULAR TAMANHO CORRETO PARA 8 SEGUNDOS
            int bytesPerSecond = SAMPLE_RATE * 2; // 16-bit = 2 bytes por sample
            int totalBytes = bytesPerSecond * (RECORDING_DURATION_MS / 1000);

            Log.d(TAG, "Iniciando gravação: " + totalBytes + " bytes esperados, Buffer: " + bufferSize);

            recordingThread = new Thread(() -> {
                ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();
                byte[] buffer = new byte[bufferSize];
                int totalBytesRead = 0;
                long startTime = System.currentTimeMillis();
                int readErrors = 0;
                final int MAX_READ_ERRORS = 5;

                while (isRecording && totalBytesRead < totalBytes && readErrors < MAX_READ_ERRORS) {
                    try {
                        // ✅ VERIFICAÇÃO CONTÍNUA DE PERMISSÃO DURANTE A GRAVAÇÃO
                        if (!hasAudioPermission()) {
                            Log.w(TAG, "Permissão revogada durante a gravação");
                            callback.onRecordingError("Permissão de gravação revogada durante a operação");
                            break;
                        }

                        int bytesRead = audioRecord.read(buffer, 0, buffer.length);

                        if (bytesRead > 0) {
                            audioBuffer.write(buffer, 0, bytesRead);
                            totalBytesRead += bytesRead;
                            readErrors = 0; // Reset error counter on successful read

                            // ✅ VERIFICAR TEMPO MÁXIMO (8 segundos)
                            if (System.currentTimeMillis() - startTime >= RECORDING_DURATION_MS) {
                                Log.d(TAG, "Tempo máximo de gravação atingido");
                                break;
                            }
                        } else if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION) {
                            Log.e(TAG, "ERROR_INVALID_OPERATION");
                            readErrors++;
                        } else if (bytesRead == AudioRecord.ERROR_BAD_VALUE) {
                            Log.e(TAG, "ERROR_BAD_VALUE");
                            readErrors++;
                        } else if (bytesRead == AudioRecord.ERROR_DEAD_OBJECT) {
                            Log.e(TAG, "ERROR_DEAD_OBJECT - AudioRecord morreu");
                            callback.onRecordingError("Erro crítico no hardware de áudio");
                            break;
                        } else {
                            // bytesRead == 0 - sem dados disponíveis
                            readErrors++;
                            try {
                                Thread.sleep(10); // Pequena pausa
                            } catch (InterruptedException e) {
                                Log.d(TAG, "Thread de gravação interrompida");
                                break;
                            }
                        }

                        // ✅ VERIFICAR SE AINDA ESTÁ GRAVANDO
                        if (audioRecord != null && audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                            Log.e(TAG, "AudioRecord parou de gravar inesperadamente");
                            break;
                        }

                    } catch (SecurityException e) {
                        Log.e(TAG, "SecurityException durante gravação: Permissão revogada", e);
                        callback.onRecordingError("Permissão de gravação revogada durante a operação");
                        break;
                    } catch (Exception e) {
                        Log.e(TAG, "Erro durante gravação: " + e.getMessage());
                        readErrors++;
                        if (readErrors >= MAX_READ_ERRORS) {
                            callback.onRecordingError("Muitos erros de leitura de áudio");
                            break;
                        }
                    }
                }

                stopRecording();

                byte[] recordedData = audioBuffer.toByteArray();
                Log.d(TAG, "Gravação concluída: " + recordedData.length + " bytes, Erros: " + readErrors);

                if (recordedData.length > 2000) { // ✅ MÍNIMO DE 2KB (1 segundo de áudio)
                    try {
                        // ✅ CONVERTER PCM PARA WAV (OBRIGATÓRIO PARA ACRCLOUD)
                        byte[] wavData = convertPcmToWav(recordedData, SAMPLE_RATE, 1, 16);
                        Log.d(TAG, "WAV criado: " + wavData.length + " bytes");
                        callback.onRecordingComplete(wavData);
                    } catch (Exception e) {
                        Log.e(TAG, "Erro na conversão WAV: " + e.getMessage());
                        callback.onRecordingError("Erro ao processar áudio: " + e.getMessage());
                    }
                } else {
                    callback.onRecordingError("Áudio muito curto: " + recordedData.length + " bytes (mínimo: 2000)");
                }

            });

            recordingThread.start();

        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException no startRecording: " + e.getMessage(), e);
            callback.onRecordingError("Erro de segurança: Permissão de gravação não disponível");
            stopRecording(); // Garantir limpeza
        } catch (Exception e) {
            Log.e(TAG, "Erro ao iniciar gravação: " + e.getMessage(), e);
            callback.onRecordingError("Erro ao iniciar gravação: " + e.getMessage());
            stopRecording(); // Garantir limpeza
        }
    }

    /**
     * ✅ VERIFICA SE A CONFIGURAÇÃO É SUPORTADA PELO DISPOSITIVO
     */
    private boolean isConfigurationSupported() {
        try {
            // ✅ VERIFICAR PERMISSÃO ANTES DE TESTAR CONFIGURAÇÃO
            if (!hasAudioPermission()) {
                Log.w(TAG, "Sem permissão para testar configuração de áudio");
                return false;
            }

            int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            if (bufferSize <= 0) {
                Log.e(TAG, "Configuração não suportada - bufferSize: " + bufferSize);
                return false;
            }

            // Testar criação do AudioRecord com tratamento de segurança
            AudioRecord testRecord;
            try {
                testRecord = new AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        bufferSize
                );
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException ao testar configuração: " + e.getMessage());
                return false;
            }

            boolean supported = testRecord.getState() == AudioRecord.STATE_INITIALIZED;
            testRecord.release();

            Log.d(TAG, "Configuração suportada: " + supported);
            return supported;

        } catch (Exception e) {
            Log.e(TAG, "Erro ao verificar configuração: " + e.getMessage());
            return false;
        }
    }

    /**
     * ✅ CONVERTE PCM BRUTO PARA FORMADO WAV (OBRIGATÓRIO PARA ACRCLOUD)
     */
    private byte[] convertPcmToWav(byte[] pcmData, int sampleRate, int channels, int bitsPerSample) {
        try {
            ByteArrayOutputStream wavBuffer = new ByteArrayOutputStream();

            // CABEÇALHO WAV
            byte[] header = createWavHeader(pcmData.length, sampleRate, channels, bitsPerSample);
            wavBuffer.write(header);
            wavBuffer.write(pcmData);

            byte[] wavData = wavBuffer.toByteArray();
            wavBuffer.close();

            Log.d(TAG, "Conversão WAV: PCM " + pcmData.length + " bytes -> WAV " + wavData.length + " bytes");
            return wavData;

        } catch (IOException e) {
            Log.e(TAG, "Erro ao converter para WAV: " + e.getMessage());
            return pcmData; // Fallback: retorna PCM se der erro
        }
    }

    /**
     * ✅ CRIA CABEÇALHO WAV CORRETO
     */
    private byte[] createWavHeader(int pcmDataLength, int sampleRate, int channels, int bitsPerSample) {
        ByteArrayOutputStream header = new ByteArrayOutputStream();

        try {
            int byteRate = sampleRate * channels * bitsPerSample / 8;
            int blockAlign = channels * bitsPerSample / 8;
            int totalDataLen = pcmDataLength + 36;

            // RIFF header
            header.write("RIFF".getBytes()); // ChunkID
            header.write(intToByteArray(totalDataLen), 0, 4); // ChunkSize
            header.write("WAVE".getBytes()); // Format

            // fmt subchunk
            header.write("fmt ".getBytes()); // Subchunk1ID
            header.write(intToByteArray(16), 0, 4); // Subchunk1Size (16 para PCM)
            header.write(shortToByteArray((short) 1), 0, 2); // AudioFormat (1 = PCM)
            header.write(shortToByteArray((short) channels), 0, 2); // NumChannels
            header.write(intToByteArray(sampleRate), 0, 4); // SampleRate
            header.write(intToByteArray(byteRate), 0, 4); // ByteRate
            header.write(shortToByteArray((short) blockAlign), 0, 2); // BlockAlign
            header.write(shortToByteArray((short) bitsPerSample), 0, 2); // BitsPerSample

            // data subchunk
            header.write("data".getBytes()); // Subchunk2ID
            header.write(intToByteArray(pcmDataLength), 0, 4); // Subchunk2Size

            byte[] headerBytes = header.toByteArray();
            header.close();
            return headerBytes;

        } catch (IOException e) {
            Log.e(TAG, "Erro ao criar cabeçalho WAV: " + e.getMessage());
            return new byte[0];
        }
    }

    // ✅ MÉTODOS AUXILIARES PARA CONVERSÃO (LITTLE ENDIAN)
    private byte[] intToByteArray(int value) {
        return new byte[] {
                (byte) (value & 0xff),
                (byte) ((value >> 8) & 0xff),
                (byte) ((value >> 16) & 0xff),
                (byte) ((value >> 24) & 0xff)
        };
    }

    private byte[] shortToByteArray(short value) {
        return new byte[] {
                (byte) (value & 0xff),
                (byte) ((value >> 8) & 0xff)
        };
    }

    /**
     * ✅ MÉTODO STOP CORRIGIDO COM VERIFICAÇÕES DE SEGURANÇA
     */
    public void stopRecording() {
        isRecording = false;

        if (audioRecord != null) {
            try {
                if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop();
                    Log.d(TAG, "AudioRecord parado");
                }
                audioRecord.release();
                audioRecord = null;
                Log.d(TAG, "AudioRecord liberado");
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException ao parar gravação: " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "Erro ao parar gravação: " + e.getMessage());
            }
        }

        if (recordingThread != null) {
            try {
                recordingThread.interrupt();
                recordingThread.join(500); // Timeout de 500ms
            } catch (InterruptedException e) {
                Log.e(TAG, "Erro ao interromper thread: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
            recordingThread = null;
        }
    }

    public boolean isRecording() {
        return isRecording;
    }

    /**
     * ✅ LIMPEZA DE RECURSOS
     */
    public void cleanup() {
        stopRecording();
    }
}