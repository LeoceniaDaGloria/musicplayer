Music Player

Bem-vindo ao Music Player, um aplicativo Android para reprodução de músicas locais, gerenciamento de playlists, reconhecimento de músicas via ACRCloud e organização de faixas favoritas e artistas. O projeto utiliza a arquitetura MVP (Model-View-Presenter) para garantir separação de responsabilidades e fácil manutenção.

Funcionalidades:

Reprodução de Músicas: Reprodução de faixas locais com controles de play/pause, avançar, retroceder, modo aleatório (shuffle) e repetição.

Gerenciamento de Playlists: Criação, edição e exclusão de playlists, com suporte para adicionar e remover músicas.
Favoritos: Marcação e visualização de músicas favoritas.

Artistas: Listagem de artistas com suas respectivas músicas.

Reconhecimento de Músicas: Identificação de músicas usando o serviço ACRCloud via gravação de áudio.

Interface Intuitiva: Suporte a temas claro/escuro, animações (ex.: rotação de capa de álbum) e navegação fluida.

Notificações: Controle de reprodução via notificações persistentes.

Gerenciamento de Músicas: Renomeação, exclusão e alteração de capas de músicas.


Pré-requisitos

Android Studio (versão 4.0 ou superior)

Dispositivo Android com API 21 (Lollipop) ou superior

Credenciais válidas do ACRCloud para reconhecimento de músicas

Permissões de armazenamento e microfone habilitadas


Instalação

Clone o Repositório:

git clone <URL_DO_REPOSITORIO>



Configuração do ACRCloud:

Crie uma conta no ACRCloud e obtenha as credenciais (Access Key e Secret).

Adicione as credenciais em app/src/main/java/com/example/musicplayer/utils/ACRCloudHelper.java.


Abra no Android Studio:

Importe o projeto no Android Studio.

Sincronize o projeto com o Gradle.


Permissões:

Certifique-se de que as permissões de armazenamento (READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE) e microfone (RECORD_AUDIO) estão configuradas no AndroidManifest.xml.


Build e Execução:

Conecte um dispositivo Android ou use um emulador.

Execute o projeto pelo Android Studio.


Uso

Tela Inicial (MainActivity):

Lista todas as músicas disponíveis no dispositivo.


Permite busca, acesso a playlists, favoritos, artistas e reconhecimento de músicas.


Reprodução de Músicas (MusicPlayerActivity):

Exibe capa do álbum, título, artista e controles de reprodução.

Suporta modo aleatório e repetição.

Animação de rotação na capa do álbum durante a reprodução.

Playlists (PlaylistsActivity, PlaylistDetailsActivity, AddToPlaylistActivity):


Crie, edite ou exclua playlists.

Adicione ou remova músicas de playlists específicas.

Favoritos (FavoritasActivity):

Visualize e gerencie músicas marcadas como favoritas.

Artistas (ArtistsActivity):

Liste artistas e suas músicas associadas.

Reconhecimento de Músicas (ReconhecerMusicaActivity):

Grave áudio para identificar músicas usando o ACRCloud.

Exibe título e artista reconhecidos, com opção de reprodução (se a música estiver na biblioteca).

Estrutura do Projeto

A seguir, a estrutura completa do projeto com a descrição de cada arquivo:

app/
├── src/
│   ├── main/
│   │   ├── java/com/example/musicplayer/
│   │   │   ├── adapter/
│   │   │   │   ├── ArtistsAdapter.java
│   │   │   │   │   └── Adaptador para exibir lista de artistas em RecyclerView.
│   │   │   │   ├── PlaylistAdapter.java
│   │   │   │   │   └── Adaptador para exibir lista de playlists em RecyclerView.
│   │   │   │   ├── SongAdapter.java
│   │   │   │   │   └── Adaptador para exibir lista de músicas em RecyclerView, com suporte a cliques e menus de contexto.
│   │   │   │
│   │   │   ├── db/
│   │   │   │   ├── AppDatabase.java
│   │   │   │   │   └── Configuração do banco de dados Room para armazenamento de músicas e playlists.
│   │   │   │   ├── MusicRepository.java
│   │   │   │   │   └── Repositório para gerenciar operações no banco de dados (músicas, playlists, favoritos).
│   │   │   │   ├── PlaylistDao.java
│   │   │   │   │   └── Interface DAO para operações CRUD em playlists.
│   │   │   │   ├── SongDao.java
│   │   │   │   │   └── Interface DAO para operações CRUD em músicas.
│   │   │   │
│   │   │   ├── dialog/
│   │   │   │   ├── DialogChangeCover.java
│   │   │   │   │   └── Diálogo para alterar a capa de uma música.
│   │   │   │   ├── DialogDeleteSong.java
│   │   │   │   │   └── Diálogo para confirmar exclusão de uma música.
│   │   │   │   ├── DialogMoveToPlaylist.java
│   │   │   │   │   └── Diálogo para mover uma música para uma playlist.
│   │   │   │   ├── DialogRenameSong.java
│   │   │   │   │   └── Diálogo para renomear uma música.
│   │   │   │   ├── DialogSongDetails.java
│   │   │   │   │   └── Diálogo para exibir detalhes de uma música.
│   │   │   │
│   │   │   ├── model/
│   │   │   │   ├── Playlist.java
│   │   │   │   │   └── Modelo de dados para playlists, com atributos como ID, nome e descrição.
│   │   │   │   ├── PlaylistSong.java
│   │   │   │   │   └── Modelo para mapeamento de relação entre músicas e playlists.
│   │   │   │   ├── Song.java
│   │   │   │   │   └── Modelo de dados para músicas, com atributos como título, artista, caminho e favorito.
│   │   │   │
│   │   │   ├── presenter/
│   │   │   │   ├── MusicPresenter.java
│   │   │   │   │   └── Presenter da arquitetura MVP, gerencia lógica de negócios para reprodução e gerenciamento de músicas.
│   │   │   │   ├── PlaylistPresenter.java
│   │   │   │   │   └── Presenter para gerenciamento de playlists (criação, edição, exclusão).
│   │   │   │
│   │   │   ├── services/
│   │   │   │   ├── MusicReceiver.java
│   │   │   │   │   └── BroadcastReceiver para lidar com ações de notificação (play/pause, próximo, anterior).
│   │   │   │   ├── MusicService.java
│   │   │   │   │   └── Serviço em foreground para reprodução contínua de músicas e notificações.
│   │   │   │
│   │   │   ├── utils/
│   │   │   │   ├── ACRCloudHelper.java
│   │   │   │   │   └── Utilitário para integração com o serviço ACRCloud para reconhecimento de músicas.
│   │   │   │   ├── AudioRecorderHelper.java
│   │   │   │   │   └── Utilitário para gravação de áudio para reconhecimento de músicas.
│   │   │   │   ├── FileUtils.java
│   │   │   │   │   └── Utilitário para manipulação de arquivos (ex.: leitura de metadados de músicas).
│   │   │   │   ├── ImageUtils.java
│   │   │   │   │   └── Utilitário para manipulação de imagens (ex.: capas de álbuns).
│   │   │   │   ├── MusicNavigationHelper.java
│   │   │   │   │   └── Utilitário para navegação entre telas do aplicativo.
│   │   │   │   ├── TimeUtils.java
│   │   │   │   │   └── Utilitário para formatação de tempo (ex.: duração de músicas).
│   │   │   │
│   │   │   ├── view/
│   │   │   │   ├── AddToPlaylistActivity.java
│   │   │   │   │   └── Activity para adicionar músicas a uma playlist.
│   │   │   │   ├── ArtistsActivity.java
│   │   │   │   │   └── Activity para listar artistas e suas músicas.
│   │   │   │   ├── DescarregadasActivity.java
│   │   │   │   │   └── Activity para listar músicas armazenadas localmente.
│   │   │   │   ├── FavoritasActivity.java
│   │   │   │   │   └── Activity para listar músicas favoritas.
│   │   │   │   ├── MainActivity.java
│   │   │   │   │   └── Activity principal com lista de músicas e navegação.
│   │   │   │   ├── MusicPlayerActivity.java
│   │   │   │   │   └── Activity para reprodução de músicas com controles e animações.
│   │   │   │   ├── PlaylistDetailsActivity.java
│   │   │   │   │   └── Activity para visualizar e gerenciar detalhes de uma playlist.
│   │   │   │   ├── PlaylistsActivity.java
│   │   │   │   │   └── Activity para listar todas as playlists.
│   │   │   │   ├── ReconhecerMusicaActivity.java
│   │   │   │   │   └── Activity para reconhecimento de músicas via ACRCloud.
│   │   │   │   ├── MusicView.java
│   │   │   │   │   └── Interface para comunicação entre Presenter e Views na arquitetura MVP.
│   │   │   │   ├── PlaylistContract.java
│   │   │   │   │   └── Contrato MVP para gerenciamento de playlists.
│   │   │
│   │   └── res/
│   │       ├── anim/
│   │       │   ├── slide_in_left.xml
│   │       │   │   └── Animação para entrada de telas (deslizar da esquerda).
│   │       │   ├── slide_out_right.xml
│   │       │   │   └── Animação para saída de telas (deslizar para a direita).
│   │       │
│   │       ├── drawable/
│   │       │   ├── album.xml
│   │       │   │   └── Recurso para exibição de capas de álbuns.
│   │       │   ├── album_art_background.xml
│   │       │   │   └── Fundo para capas de álbuns.
│   │       │   ├── chevron_right.xml
│   │       │   │   └── Ícone de seta para a direita.
│   │       │   ├── circle_background.xml
│   │       │   │   └── Fundo circular para elementos de UI.
│   │       │   ├── ic_add.xml
│   │       │   │   └── Ícone para adicionar itens.
│   │       │   ├── ic_add_music.xml
│   │       │   │   └── Ícone para adicionar músicas.
│   │       │   ├── ic_chevron_right.xml
│   │       │   │   └── Ícone de seta para a direita (alternativo).
│   │       │   ├── ic_delete.xml
│   │       │   │   └── Ícone para exclusão.
│   │       │   ├── ic_lm_capa_placeholder.xml
│   │       │   │   └── Imagem padrão para capas de álbuns.
│   │       │   ├── ic_pause.xml
│   │       │   │   └── Ícone de pausa.
│   │       │   ├── ic_photo_camera.xml
│   │       │   │   └── Ícone de câmera para alterar capas.
│   │       │   ├── ic_photo_library.xml
│   │       │   │   └── Ícone de galeria para alterar capas.
│   │       │   ├── ic_play_arrow.xml
│   │       │   │   └── Ícone de reprodução.
│   │       │   ├── ic_repeat.xml
│   │       │   │   └── Ícone de repetição (desativado).
│   │       │   ├── ic_repeat_on.xml
│   │       │   │   └── Ícone de repetição (ativado).
│   │       │   ├── ic_shuffle.xml
│   │       │   │   └── Ícone de modo aleatório (desativado).
│   │       │   ├── ic_shuffle_on.xml
│   │       │   │   └── Ícone de modo aleatório (ativado).
│   │       │   ├── ic_skip_next.xml
│   │       │   │   └── Ícone de próxima faixa.
│   │       │   ├── ic_skip_previous.xml
│   │       │   │   └── Ícone de faixa anterior.
│   │       │   ├── ic_star.xml
│   │       │   │   └── Ícone de favorito (preenchido).
│   │       │   ├── ic_star_outline.xml
│   │       │   │   └── Ícone de favorito (contorno).
│   │       │   ├── ic_warning.xml
│   │       │   │   └── Ícone de alerta.
│   │       │   ├── track_thumb.xml
│   │       │   │   └── Indicador do SeekBar para progresso de música.
│   │       │
│   │       ├── layout/
│   │       │   ├── activity_add_to_playlist.xml
│   │       │   │   └── Layout para a tela de adicionar músicas a playlists.
│   │       │   ├── activity_artists.xml
│   │       │   │   └── Layout para a tela de listagem de artistas.
│   │       │   ├── activity_descarregadas.xml
│   │       │   │   └── Layout para a tela de músicas armazenadas localmente.
│   │       │   ├── activity_favoritas.xml
│   │       │   │   └── Layout para a tela de músicas favoritas.
│   │       │   ├── activity_main.xml
│   │       │   │   └── Layout para a tela principal do aplicativo.
│   │       │   ├── activity_music_player.xml
│   │       │   │   └── Layout para a tela de reprodução de músicas.
│   │       │   ├── activity_playlist_details.xml
│   │       │   │   └── Layout para a tela de detalhes de uma playlist.
│   │       │   ├── activity_playlists.xml
│   │       │   │   └── Layout para a tela de listagem de playlists.
│   │       │   ├── activity_reconhecer_musica.xml
│   │       │   │   └── Layout para a tela de reconhecimento de músicas.
│   │       │   ├── artists_item.xml
│   │       │   │   └── Layout para cada item de artista na lista.
│   │       │   ├── custom_music_notification.xml
│   │       │   │   └── Layout para a notificação de reprodução.
│   │       │   ├── dialog_change_cover.xml
│   │       │   │   └── Layout para o diálogo de alteração de capa.
│   │       │   ├── dialog_create_playlist.xml
│   │       │   │   └── Layout para o diálogo de criação de playlist.
│   │       │   ├── dialog_delete_song.xml
│   │       │   │   └── Layout para o diálogo de exclusão de música.
│   │       │   ├── dialog_move_to_playlist.xml
│   │       │   │   └── Layout para o diálogo de mover música para playlist.
│   │       │   ├── dialog_rename_song.xml
│   │       │   │   └── Layout para o diálogo de renomeação de música.
│   │       │   ├── dialog_song_details.xml
│   │       │   │   └── Layout para o diálogo de detalhes de música.
│   │       │   ├── item_playlist.xml
│   │       │   │   └── Layout para cada item de playlist na lista.
│   │       │   ├── item_song_selection.xml
│   │       │   │   └── Layout para seleção de músicas em playlists.
│   │       │   ├── list_item_musica.xml
│   │       │   │   └── Layout para cada item de música na lista.
│   │       │
│   │       ├── menu/
│   │       │   ├── main_menu.xml
│   │       │   │   └── Menu principal com opções de navegação (favoritas, playlists, etc.).
│   │       │   ├── playlists_menu.xml
│   │       │   │   └── Menu para ações em playlists.
│   │       │   ├── song_context_menu.xml
│   │       │   │   └── Menu de contexto para ações em músicas (ex.: renomear, excluir).
│   │       │
│   │       ├── values/
│   │       │   ├── colors.xml
│   │       │   │   └── Definições de cores para temas claro e escuro.
│   │       │   ├── dimens.xml
│   │       │   │   └── Definições de dimensões (ex.: margens, tamanhos de texto).
│   │       │   ├── strings.xml
│   │       │   │   └── Strings de texto usadas no aplicativo.
│   │       │   ├── styles.xml
│   │       │   │   └── Estilos para componentes de UI (ex.: botões, barras).
│   │       │   ├── themes.xml
│   │       │   │   └── Temas claro e escuro do aplicativo.

Contribuição
Contribuições são bem-vindas! Para contribuir:

Faça um fork do repositório.

Crie uma branch para sua feature (git checkout -b feature/nova-funcionalidade).

Commit suas alterações (git commit -m 'Adiciona nova funcionalidade').

Push para a branch (git push origin feature/nova-funcionalidade).

Abra um Pull Request.

 Configuração do Firebase

Este projeto utiliza o Firebase para Analytics, Cloud Messaging, Firestore e Autenticação. Siga os passos abaixo para configurar:

1. Crie um Projeto no Firebase:
   - Acesse [Firebase Console](https://console.firebase.google.com/) e crie um projeto.
   - Adicione o app Android com o pacote `com.example.musicplayer`.

2. 2Adicione o google-services.json:
   - Baixe o arquivo `google-services.json` e coloque na pasta `app/`.

3. pendências:
   - As dependências do Firebase estão configuradas no `libs.versions.toml` usando o Firebase BOM:
     toml
     [versions]
     firebase-bom = "33.1.2"
     [libraries]
     firebase-bom = { module = "com.google.firebase:firebase-bom", version.ref = "firebase-bom" }
     firebase-analytics = { module = "com.google.firebase:firebase-analytics" }
     firebase-messaging = { module = "com.google.firebase:firebase-messaging" }
     firebase-firestore = { module = "com.google.firebase:firebase-firestore" }
     firebase-auth = { module = "com.google.firebase:firebase-auth" }
