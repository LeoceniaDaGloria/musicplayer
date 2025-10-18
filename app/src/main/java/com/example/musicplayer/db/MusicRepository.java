package com.example.musicplayer.db;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.room.Room;

import com.example.musicplayer.R;
import com.example.musicplayer.model.Playlist;
import com.example.musicplayer.model.PlaylistSong;
import com.example.musicplayer.model.Song;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Repositório para gerenciar dados de músicas e playlists.
 * Lida com Room para armazenamento local e MediaStore para carregar músicas do dispositivo.
 */
public class MusicRepository {
    private static final String TAG = "MusicRepository";
    private AppDatabase db;
    private SongDao songDao;
    private PlaylistDao playlistDao;
    private boolean hasLoadedInitialData = false;
    private Context context;

    public MusicRepository(Context context) {
        if (context == null) {
            Log.e(TAG, "Context is null!");
            return;
        }
        this.context = context;
        db = Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, "music_db")
                .fallbackToDestructiveMigration()
                .build();
        songDao = db.songDao();
        playlistDao = db.playlistDao();
        Log.d(TAG, "Repository initialized");
    }


    /**
     *  Adiciona música à playlist (versão com objeto Song)
     */
    public void addSongToPlaylist(long playlistId, Song song) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                // Verifica se a música já está na playlist
                int count = playlistDao.isSongInPlaylist(playlistId, song.getId());
                if (count == 0) {
                    PlaylistSong playlistSong = new PlaylistSong(playlistId, song.getId());
                    playlistDao.insertPlaylistSong(playlistSong);

                    // Atualiza a contagem de músicas
                    updatePlaylistSongCount(playlistId);

                    Log.d(TAG, "✅ Música '" + song.getTitle() + "' adicionada à playlist ID: " + playlistId);
                } else {
                    Log.d(TAG, "⚠️ Música '" + song.getTitle() + "' já está na playlist ID: " + playlistId);
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ Erro ao adicionar música à playlist: " + e.getMessage());
            }
        });
    }

    /**
     *  Atualiza contagem automaticamente
     */
    private void updatePlaylistSongCount(long playlistId) {
        try {
            int count = playlistDao.getSongCountForPlaylistSync(playlistId);
            playlistDao.updateSongCount(playlistId, count);
            Log.d(TAG, "✅ Contagem atualizada - Playlist " + playlistId + ": " + count + " músicas");
        } catch (Exception e) {
            Log.e(TAG, "❌ Erro ao atualizar contagem: " + e.getMessage());
        }
    }

    /**
     * Verifica se música está na playlist (com objeto Song)
     */
    public boolean isSongInPlaylist(long playlistId, Song song) {
        try {
            return db.runInTransaction(() -> {
                int count = playlistDao.isSongInPlaylist(playlistId, song.getId());
                return count > 0;
            });
        } catch (Exception e) {
            Log.e(TAG, "❌ Erro ao verificar se música está na playlist: " + e.getMessage());
            return false;
        }
    }

    /**
     * Remove música com objeto Song
     */
    public void removeSongFromPlaylist(long playlistId, Song song) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                playlistDao.deletePlaylistSong(playlistId, song.getId());
                updatePlaylistSongCount(playlistId);
                Log.d(TAG, "✅ Música '" + song.getTitle() + "' removida da playlist ID: " + playlistId);
            } catch (Exception e) {
                Log.e(TAG, "❌ Erro ao remover música da playlist: " + e.getMessage());
            }
        });
    }

    public void loadSongsFromDeviceIfNeeded(Context context) {
        Executors.newSingleThreadExecutor().execute(() -> {
            int existingSongsCount = songDao.getCount();

            if (existingSongsCount == 0) {
                Log.d(TAG, "First time loading songs from device");
                loadSongsFromDevice(context);
            } else {
                Log.d(TAG, "Already have " + existingSongsCount + " songs in database");
                checkForNewSongs(context);
                hasLoadedInitialData = true;
            }
        });
    }

    private void checkForNewSongs(Context context) {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ?
                Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission " + permission + " not granted, cannot check for new songs");

            // NOVO: Se não tem permissão, tenta carregar do raw
            loadSongsFromRaw(context);
            return;
        }

        Log.d(TAG, "Checking for new songs...");
        ContentResolver contentResolver = context.getContentResolver();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DURATION
        };
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        Cursor cursor = null;

        try {
            cursor = contentResolver.query(uri, projection, selection, null, null);
            if (cursor == null || cursor.getCount() == 0) {
                Log.e(TAG, "Cursor is null or empty, loading from raw");
                // NOVO: Se não encontrar músicas, carrega do raw
                loadSongsFromRaw(context);
                return;
            }

            List<Song> newSongs = new ArrayList<>();
            while (cursor.moveToNext()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID));
                String title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE));
                String artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST));
                String data = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));
                int duration = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION));

                Song existingSong = songDao.getSongByPath(data);
                if (existingSong == null) {
                    Song newSong = new Song(id, title, artist, data, duration);
                    newSongs.add(newSong);
                    Log.d(TAG, "New song found: " + title + " - " + artist);
                }
            }

            if (!newSongs.isEmpty()) {
                for (Song song : newSongs) {
                    songDao.insert(song);
                }
                Log.d(TAG, "Added " + newSongs.size() + " new songs to database");
            } else {
                Log.d(TAG, "No new songs found");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error checking for new songs: " + e.getMessage());
            // NOVO: Em caso de erro, tenta carregar do raw
            loadSongsFromRaw(context);
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public void loadSongsFromDevice(Context context) {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ?
                Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission " + permission + " not granted, cannot load songs");
            // NOVO: Se não tem permissão, carrega do raw
            loadSongsFromRaw(context);
            return;
        }

        Log.d(TAG, "Starting to load songs from MediaStore with permission: " + permission);
        ContentResolver contentResolver = context.getContentResolver();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DURATION
        };
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        Cursor cursor = null;

        try {
            cursor = contentResolver.query(uri, projection, selection, null, null);
            if (cursor == null || cursor.getCount() == 0) {
                Log.e(TAG, "Cursor is null or empty, no songs found in device, loading from raw");
                // NOVO: Se não encontrar músicas, carrega do raw
                loadSongsFromRaw(context);
                return;
            }

            List<Song> songs = new ArrayList<>();
            while (cursor.moveToNext()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID));
                String title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE));
                String artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST));
                String data = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));
                int duration = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION));
                Song song = new Song(id, title, artist, data, duration);
                songs.add(song);
                Log.d(TAG, "Loaded song: " + title + " by " + artist + ", path: " + data);
            }

            if (!songs.isEmpty()) {
                Executors.newSingleThreadExecutor().execute(() -> {
                    for (Song song : songs) {
                        if (songDao.getSongById(song.getId()) == null) {
                            songDao.insert(song);
                        }
                    }
                    Log.d(TAG, "Inserted/updated " + songs.size() + " songs into database");
                    hasLoadedInitialData = true;

                    // EXTRAR METADADOS APÓS CARREGAR AS MÚSICAS
                    extractMetadataForSongs(songs, context);
                });
            } else {
                Log.w(TAG, "No songs found in MediaStore, loading from raw");
                // NOVO: Se não encontrar músicas, carrega do raw
                loadSongsFromRaw(context);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading songs from MediaStore: " + e.getMessage());
            // NOVO: Em caso de erro, carrega do raw
            loadSongsFromRaw(context);
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
    /**
     * ✅ DEBUG: Verificar relação específica no banco
     */
    public boolean isSongInPlaylistSync(long playlistId, long songId) {
        try {
            return db.runInTransaction(() -> {
                List<PlaylistSong> relations = db.playlistDao().getPlaylistSongRelationSync(playlistId, songId);
                return relations != null && !relations.isEmpty();
            });
        } catch (Exception e) {
            Log.e(TAG, "Erro ao verificar relação: " + e.getMessage());
            return false;
        }
    }

    /**
     * Carrega músicas da pasta raw
     */
    private void loadSongsFromRaw(Context context) {
        Log.d(TAG, "Loading songs from raw folder...");

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                List<Song> rawSongs = new ArrayList<>();
                Field[] fields = R.raw.class.getFields();

                Log.d(TAG, "Found " + fields.length + " raw resources");

                for (int i = 0; i < fields.length; i++) {
                    try {
                        String resourceName = fields[i].getName();
                        int resourceId = fields[i].getInt(null);

                        // Cria um ID único para músicas do raw (usando negativo para diferenciar)
                        long rawId = -1L * (i + 1);

                        // Formata o nome para exibição
                        String formattedTitle = formatRawFileName(resourceName);

                        // Tenta obter o tamanho do recurso
                        long size = 0;
                        try {
                            android.content.res.AssetFileDescriptor afd = context.getResources().openRawResourceFd(resourceId);
                            size = afd.getLength();
                            afd.close();
                        } catch (Resources.NotFoundException e) {
                            Log.w(TAG, "Could not get size for raw resource: " + resourceName);
                        }

                        // Cria a música com path especial para identificar que é do raw
                        Song song = new Song(
                                rawId,
                                formattedTitle,
                                "Artista Desconhecido",
                                "raw://" + resourceId, // URI especial para raw
                                0, // Duração será extraída depois
                                size,
                                resourceId
                        );

                        rawSongs.add(song);
                        Log.d(TAG, "Loaded raw song: " + formattedTitle + " (resource: " + resourceName + ", size: " + size + " bytes)");

                    } catch (Exception e) {
                        Log.e(TAG, "Error loading raw resource: " + fields[i].getName(), e);
                    }
                }

                if (!rawSongs.isEmpty()) {
                    // Insere no banco de dados
                    for (Song song : rawSongs) {
                        // Verifica se já existe
                        Song existingSong = songDao.getSongByPath(song.getPath());
                        if (existingSong == null) {
                            songDao.insert(song);
                        }
                    }

                    Log.d(TAG, "Inserted " + rawSongs.size() + " raw songs into database");
                    hasLoadedInitialData = true;

                    // Extrai metadados para as músicas do raw
                    extractMetadataForRawSongs(rawSongs, context);

                } else {
                    Log.w(TAG, "No songs found in raw folder");
                }

            } catch (Exception e) {
                Log.e(TAG, "Error loading songs from raw folder: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Formata o nome do arquivo raw para exibição
     */
    private String formatRawFileName(String fileName) {
        // Remove números no final (como musica1, musica2) e formata
        String formatted = fileName
                .replaceAll("([0-9]+)$", " $1") // Mantém números mas separa com espaço
                .replace("_", " ")
                .replace("-", " ")
                .trim();

        // Capitaliza as palavras
        String[] words = formatted.split("\\s+");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (!word.isEmpty()) {
                if (result.length() > 0) {
                    result.append(" ");
                }
                result.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1).toLowerCase());
            }

        }



        // Se ficou vazio, usa o nome original
        if (result.length() == 0) {
            return fileName;
        }

        return result.toString();
    }

    /**
     * NOVO: Extrai metadados para músicas da pasta raw
     */
    private void extractMetadataForRawSongs(List<Song> rawSongs, Context context) {
        new Thread(() -> {
            Log.w("DEBUG_RAW", "=== INICIANDO EXTRACAO DE METADADOS PARA RAW ===");

            for (Song song : rawSongs) {
                try {
                    if (song.isFromRaw()) {
                        Log.w("DEBUG_RAW", "Processando música raw: " + song.getTitle());

                        // Extrai metadados da música raw
                        song.extractAllMetadata(context);
                        songDao.update(song);

                        Log.w("DEBUG_RAW", "Metadados extraídos para: " + song.getTitle() + " - Duração: " + song.getDuration());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Erro ao extrair metadados para raw: " + song.getTitle());
                    Log.e("DEBUG_RAW", "Erro: " + e.getMessage());
                }
            }

            Log.w("DEBUG_RAW", "=== EXTRACAO DE METADADOS RAW CONCLUÍDA ===");
        }).start();
    }

    /**
     *  Metodo para pré-extrair metadados COM DEBUG
     */
    private void extractMetadataForSongs(List<Song> songs, Context context) {
        new Thread(() -> {
            Log.w("DEBUG_PLAYLIST", "=== INICIANDO EXTRACAO DE METADADOS ===");

            // VERIFICAR ESTADO ANTES DA EXTRACAO
            List<Playlist> playlistsBefore = getAllPlaylistsSync();
            Log.w("DEBUG_PLAYLIST", "ESTADO INICIAL:");
            Log.w("DEBUG_PLAYLIST", "   - Playlists no banco: " + playlistsBefore.size());
            Log.w("DEBUG_PLAYLIST", "   - Músicas a processar: " + songs.size());

            for (Playlist playlist : playlistsBefore) {
                Log.w("DEBUG_PLAYLIST", "   - Playlist existente: '" + playlist.getName() +
                        "' | ID: " + playlist.getId());
            }

            int processedSongs = 0;

            for (Song song : songs) {
                try {
                    processedSongs++;
                    Log.w("DEBUG_PLAYLIST", "Processando música " + processedSongs + "/" + songs.size() +
                            ": " + song.getTitle());

                    // VERIFICAR ESTADO ANTES DE CADA MÚSICA
                    List<Playlist> playlistsDuring = getAllPlaylistsSync();
                    Log.w("DEBUG_PLAYLIST", "   Playlists antes de '" + song.getTitle() + "': " + playlistsDuring.size());

                    song.extractAllMetadata(context);
                    songDao.update(song);

                    // VERIFICAR ESTADO DEPOIS DE CADA MÚSICA
                    List<Playlist> playlistsAfterSong = getAllPlaylistsSync();
                    Log.w("DEBUG_PLAYLIST", "   Playlists depois de '" + song.getTitle() + "': " + playlistsAfterSong.size());

                    if (playlistsAfterSong.size() > playlistsDuring.size()) {
                        Log.e("DEBUG_PLAYLIST", "PROBLEMA DETECTADO!");
                        Log.e("DEBUG_PLAYLIST", "   Playlist criada durante processamento de: " + song.getTitle());

                        // Identificar qual playlist foi criada
                        for (Playlist newPlaylist : playlistsAfterSong) {
                            boolean isNew = true;
                            for (Playlist oldPlaylist : playlistsDuring) {
                                if (oldPlaylist.getId() == newPlaylist.getId()) {
                                    isNew = false;
                                    break;
                                }
                            }
                            if (isNew) {
                                Log.e("DEBUG_PLAYLIST", "   NOVA PLAYLIST: '" + newPlaylist.getName() +
                                        "' | ID: " + newPlaylist.getId());

                                // Verificar músicas nesta nova playlist
                                List<Song> songsInNewPlaylist = getSongsForPlaylistSync(newPlaylist.getId());
                                Log.e("DEBUG_PLAYLIST", "   Músicas na nova playlist: " + songsInNewPlaylist.size());
                                for (Song s : songsInNewPlaylist) {
                                    Log.e("DEBUG_PLAYLIST", "     - " + s.getTitle());
                                }
                            }
                        }
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Erro ao extrair metadados para: " + song.getTitle());
                    Log.e("DEBUG_PLAYLIST", "Erro ao processar: " + song.getTitle() + " - " + e.getMessage());
                }
            }

            // VERIFICAR ESTADO FINAL
            List<Playlist> playlistsAfter = getAllPlaylistsSync();
            Log.w("DEBUG_PLAYLIST", "=== RESUMO FINAL ===");
            Log.w("DEBUG_PLAYLIST", "   - Playlists antes: " + playlistsBefore.size());
            Log.w("DEBUG_PLAYLIST", "   - Playlists depois: " + playlistsAfter.size());
            Log.w("DEBUG_PLAYLIST", "   - Músicas processadas: " + processedSongs + "/" + songs.size());

            if (playlistsAfter.size() > playlistsBefore.size()) {
                Log.e("DEBUG_PLAYLIST", "CONFIRMADO: Playlists criadas durante extração de metadados!");
                Log.e("DEBUG_PLAYLIST", "   Total criado: " + (playlistsAfter.size() - playlistsBefore.size()));
            } else {
                Log.w("DEBUG_PLAYLIST", "Nenhuma playlist criada durante extração");
            }

        }).start();
    }

    /**
     * Retorna todas as músicas como LiveData.
     */
    public LiveData<List<Song>> getAllSongs() {
        return songDao.getAll();
    }

    /**
     * Retorna músicas favoritas como LiveData.
     */
    public LiveData<List<Song>> getFavorites() {
        return songDao.getFavorites();
    }

    /**
     * Retorna músicas descarregadas como LiveData.
     */
    public LiveData<List<Song>> getDownloadedSongs() {
        return songDao.getDownloadedSongs();
    }

    /**
     * Retorna músicas de uma playlist específica como LiveData.
     */
    public LiveData<List<Song>> getSongsForPlaylist(long playlistId) {
        return playlistDao.getSongsForPlaylist(playlistId);
    }

    /**
     * Retorna todas as playlists como LiveData.
     */
    public LiveData<List<Playlist>> getAllPlaylists() {
        return playlistDao.getAll();
    }

    /**
     * Método síncrono para debug
     */
    public List<Playlist> getAllPlaylistsSync() {
        try {
            return db.runInTransaction(() -> {
                return playlistDao.getAllSync();
            });
        } catch (Exception e) {
            Log.e(TAG, "Erro sync getAllPlaylists: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     *  Mtodo sincrono para obter todas as músicas
     */
    public List<Song> getAllSongsSync() {
        try {
            return db.runInTransaction(() -> {
                return songDao.getAllSync();
            });
        } catch (Exception e) {
            Log.e(TAG, "Erro sync getAllSongs: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * ADICIONADO: Método síncrono para debug
     */
    public List<Song> getSongsForPlaylistSync(long playlistId) {
        try {
            return db.runInTransaction(() -> {
                return playlistDao.getSongsForPlaylistSync(playlistId);
            });
        } catch (Exception e) {
            Log.e(TAG, "Erro sync getSongsForPlaylist: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Método síncrono para contar músicas na playlist
     */
    public int getSongCountForPlaylistSync(long playlistId) {
        try {
            return db.runInTransaction(() -> {
                return playlistDao.getSongCountForPlaylistSync(playlistId);
            });
        } catch (Exception e) {
            Log.e(TAG, "Erro sync getSongCountForPlaylist: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Insere uma música no banco de dados de forma assíncrona.
     */
    public void insertSong(Song song) {
        Executors.newSingleThreadExecutor().execute(() -> songDao.insert(song));
    }

    /**
     * Insere uma playlist no banco de dados de forma assíncrona.
     */
    public void insertPlaylist(Playlist playlist) {
        Executors.newSingleThreadExecutor().execute(() -> playlistDao.insert(playlist));
    }

    /**
     * Insere uma relação entre playlist e música no banco de dados de forma assíncrona.
     */
    public void insertPlaylistSong(PlaylistSong playlistSong) {
        Executors.newSingleThreadExecutor().execute(() -> playlistDao.insertPlaylistSong(playlistSong));
    }

    /**
     * Insere uma playlist e uma música de forma sequencial e, em seguida, cria a relação.
     */
    public void insertPlaylistAndSongWithRelation(Playlist playlist, Song song) {
        Executors.newSingleThreadExecutor().execute(() -> {
            long playlistId = playlistDao.insert(playlist);
            long songId = songDao.insert(song);
            PlaylistSong playlistSong = new PlaylistSong(playlistId, songId);
            playlistDao.insertPlaylistSong(playlistSong);
            Log.d(TAG, "Inserted initial data and created relation for playlistId: " + playlistId + " and songId: " + songId);
        });
    }

    /**
     * Adiciona uma música a uma playlist de forma assíncrona.
     */
    public void addSongToPlaylist(long playlistId, long songId) {
        Executors.newSingleThreadExecutor().execute(() -> {
            PlaylistSong playlistSong = new PlaylistSong(playlistId, songId);
            playlistDao.insertPlaylistSong(playlistSong);
            Log.d(TAG, "Added song " + songId + " to playlist " + playlistId);
        });
    }

    /**
     * Remove uma música de uma playlist de forma assíncrona.
     */
    public void removeSongFromPlaylist(long playlistId, long songId) {
        Executors.newSingleThreadExecutor().execute(() -> {
            playlistDao.deletePlaylistSong(playlistId, songId);
            Log.d(TAG, "Removed song " + songId + " from playlist " + playlistId);
        });
    }

    /**
     * Retorna artistas únicos como LiveData.
     */
    public LiveData<List<String>> getAllArtists() {
        return songDao.getAllArtists();
    }

    /**
     * Retorna músicas de um artista específico como LiveData.
     */
    public LiveData<List<Song>> getSongsByArtist(String artist) {
        return songDao.getSongsByArtist(artist);
    }

    /**
     * Toggle favorito de uma música e atualiza no banco.
     */
    public void toggleFavorite(Song song) {
        song.setFavorite(!song.isFavorite());
        Executors.newSingleThreadExecutor().execute(() -> songDao.update(song));
        Log.d(TAG, "Toggled favorite for song: " + song.getTitle() + " to " + song.isFavorite());
    }

    /**
     * Marca/desmarca uma música como descarregada.
     */
    public void toggleDownloaded(Song song) {
        song.setDownloaded(!song.isDownloaded());
        Executors.newSingleThreadExecutor().execute(() -> songDao.update(song));
        Log.d(TAG, "Toggled downloaded for song: " + song.getTitle() + " to " + song.isDownloaded());
    }

    /**
     * Cria uma nova playlist.
     */
    // NO MusicRepository.java - NO MÉTODO createPlaylist:
    public void createPlaylist(String name) {
        Executors.newSingleThreadExecutor().execute(() -> {
            Playlist playlist = new Playlist(0, name, 0, System.currentTimeMillis());
            playlistDao.insert(playlist);
            Log.d(TAG, "Created playlist: " + name);
        });
    }

    /**
     * Verifica se já carregou dados iniciais
     */
    public boolean hasLoadedInitialData() {
        return hasLoadedInitialData;
    }


    /**
     * Atualiza uma música no banco de dados
     */
    public void updateSong(Song song) {
        if (song != null) {
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    songDao.update(song);
                    Log.d(TAG, "Música atualizada no banco: " + song.getTitle());
                } catch (Exception e) {
                    Log.e(TAG, "Erro ao atualizar música: " + e.getMessage());
                }
            });
        }
    }

    /**
     * Busca uma música pelo ID
     */
    public Song getSongById(long songId) {
        try {
            return songDao.getSongById(songId);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao buscar música por ID: " + e.getMessage());
            return null;
        }
    }

    /**
     * Exclui uma música do banco de dados
     */
    public void deleteSong(Song song) {
        if (song != null) {
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    // Remove a música de todas as playlists primeiro
                    playlistDao.removeSongFromAllPlaylists(song.getId());

                    // Depois remove a música
                    songDao.delete(song);
                    Log.d(TAG, "Música excluída do banco: " + song.getTitle());
                } catch (Exception e) {
                    Log.e(TAG, "Erro ao excluir música: " + e.getMessage());
                }
            });
        }
    }

    /**
     * Remove uma música de todas as playlists
     */
    public void removeSongFromAllPlaylists(long songId) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                playlistDao.removeSongFromAllPlaylists(songId);
                Log.d(TAG, "Música removida de todas as playlists: " + songId);
            } catch (Exception e) {
                Log.e(TAG, "Erro ao remover música das playlists: " + e.getMessage());
            }
        });
    }

    /**
     * Busca uma música pelo caminho do arquivo
     */
    public Song getSongByPath(String path) {
        try {
            return songDao.getSongByPath(path);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao buscar música por path: " + e.getMessage());
            return null;
        }
    }

    /**
     * Busca músicas por título (para busca)
     */
    public LiveData<List<Song>> searchSongs(String query) {
        return songDao.searchSongs("%" + query + "%");
    }

    /**
     * Obtém a contagem total de músicas
     */
    public int getSongsCount() {
        try {
            return songDao.getCount();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao obter contagem de músicas: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Obtém a contagem total de playlists
     */
    public int getPlaylistsCount() {
        try {
            return playlistDao.getCount();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao obter contagem de playlists: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Busca uma playlist pelo ID
     */
    public Playlist getPlaylistById(long playlistId) {
        try {
            return playlistDao.getPlaylistById(playlistId);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao buscar playlist por ID: " + e.getMessage());
            return null;
        }
    }

    /**
     * Exclui uma playlist
     */
    public void deletePlaylist(Playlist playlist) {
        if (playlist != null) {
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    // Remove todas as relações primeiro
                    playlistDao.removeAllSongsFromPlaylist(playlist.getId());
                    // Depois remove a playlist
                    playlistDao.delete(playlist);
                    Log.d(TAG, "Playlist excluída: " + playlist.getName());
                } catch (Exception e) {
                    Log.e(TAG, "Erro ao excluir playlist: " + e.getMessage());
                }
            });
        }
    }

    /**
     * Remove todas as musicas de uma playlist
     */
    public void removeAllSongsFromPlaylist(long playlistId) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                playlistDao.removeAllSongsFromPlaylist(playlistId);
                Log.d(TAG, "Todas as músicas removidas da playlist: " + playlistId);
            } catch (Exception e) {
                Log.e(TAG, "Erro ao remover músicas da playlist: " + e.getMessage());
            }
        });
    }

    /**
     *  Metodo para forçar o carregamento apenas do raw
     */
    public void loadOnlyRawSongs() {
        loadSongsFromRaw(context);
    }

    /**
     *  Verifica se há músicas no storage
     */
    public boolean hasStorageMusic(Context context) {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ?
                Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        ContentResolver contentResolver = context.getContentResolver();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = { MediaStore.Audio.Media._ID };
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";

        try (Cursor cursor = contentResolver.query(uri, projection, selection, null, null)) {
            return cursor != null && cursor.getCount() > 0;
        } catch (Exception e) {
            Log.e(TAG, "Error checking storage music: " + e.getMessage());
            return false;
        }
    }
}