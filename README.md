# PlaylistTransporter

App para importar playlists de um app de streaming e exportá-las para outro app de streaming ou para um arquivo.

## Como funciona:
O app cria um server local temporário e então abre uma tela de autenticação do app de streaming. Após a autenticação sucedida, o usuário é redirecionado para a URL do server local temporário. Um *código do app de streaming* é embutido junto nessa URL, o app pega esse código e o redireciona para uma outra URL que retorna um *token* temporário do app de streaming caso o código sejá válido, esse token permite que o app converse com os servidores do app de streaming atráves de uma API e consiga os dados de playlists e músicas. 

Processo oficial descrito em: 
* https://developers.app.apps.deezer.com/api/oauth
* https://developer.app.apps.spotify.com/documentation/general/guides/authorization-guide/

## Ações atualmente suportadas
* Deezer >> Spotify - importa playlists do Deezer e cria as mesmas playlists no Spotify
* Deezer >> Arquivo - importa playlists do Deezer e cria um arquivo com as playlists
* Spotify >> Deezer - importa playlists do Spotify e cria as mesmas playlists no Deezer
* Spotify >> Arquivo - importa playlists do Spotify e cria um arquivo com as playlists
* Arquivo >> Deezer - importa playlists de um arquivo *criado* pelo PlaylistTransporter e cria as playlists no Deezer
* Arquivo >> Spotify - importa playlists de um arquivo *criado* pelo PlaylistTransporter e cria as playlists no Spotify

## Apps de streaming atualmente suportados
* Deezer (import/export) - importa e traz playlists dos servidores do Deezer; exporta e cria playlists nos servidores do Deezer
* Spotify (import/export) - importa e traz playlists dos servidores do Spotify; exporta e cria playlists nos servidores do Spotify 
   * É necessário setar como permitido o email da conta onde as playlists vão ser criadas para funcionar, entre em contato se precisar
* Arquivo (import/export) - importa e traz playlists de um arquivo criado pelo PlaylistTransporter; exporta e cria um arquivo com as playlists importadas

## Extensões de arquivos de saída atualmente suportados:
* txt
* csv

## Como usar:

### Para exportar do Deezer para o Spotify
*É necessário que o email da conta onde as playlists vão ser criadas seja setado como permitido para funcionar, entre em contato se precisar.* Baixe o zip da última release desse projeto, extraia, rode o PlaylistTransporter.jar (ou o executeWithParameters.bat caso esteja setando algum parâmetro), escolha a ação 'Deezer >> Spotify', logue com sua conta do Deezer, espere importar as playlists, logue com sua conta do Spotify e espera terminar de exportar.

### Para exportar do Spotify para o Deezer
Baixe o zip da última release desse projeto, extraia, rode o PlaylistTransporter.jar (ou o executeWithParameters.bat caso esteja setando algum parâmetro), escolha a ação 'Spotify >> Deezer', logue com sua conta do Spotify, espere importar as playlists, logue com sua conta do Deezer e espera terminar de exportar.

### Para exportar de arquivo para um app de Streaming
Baixe o zip da última release desse projeto, extraia, rode o PlaylistTransporter.jar (ou o executeWithParameters.bat caso esteja setando algum parâmetro), escolha a ação 'Arquivo >> Deezer' ou 'Arquivo >> Spotify', selecione o(s) arquivo(s) com as playlists, espere importar, logue com sua conta do Deezer/Spotify e espera terminar de exportar.

### Para exportar para arquivo
Baixe o zip da última release desse projeto, extraia, rode o PlaylistTransporter.jar (ou o executeWithParameters.bat caso esteja setando algum parâmetro), escolha a ação 'Deezer >> Arquivo' ou 'Spotify >> Arquivo', logue com sua conta do Deezer/Spotify, espere importar e exportar as playlists para o arquivo.\
Por padrão, o app exportará todas as playlists com todas as músicas em um único arquivo 'Playlists.txt' no mesmo diretório do jar.

## Parâmetros:

Você pode editar o executeWithParameters.bat para setar algum desses parâmetros abaixo, mas então precisaria executar o app rodando esse executeWithParameters.bat.\
Obs: Mantenha o ^ no final das linhas.\
Obs: O executeWithParameters.bat precisa estar na mesma pasta do PlaylistTransporter.jar

### Parâmetros opcionais globais:
* playlistToImport - Define uma playlist específica para ser importada através do Nome.
    * Exemplo - playlistToImport="Loved Tracks"
    * Padrão - todas (sem valor)
### Parâmetros opcionais de exportação para arquivo:
* saveWithName - Define o nome do arquivo de saída com as playlists e músicas.
    * Exemplo - saveWithName="Deezer Songs"
    * Padrão - 'Playlists' 
* saveAs - Define a extensão do arquivo de saída.
    * Exemplo - saveAs="csv"
    * Padrão - 'txt'
* exportFilePath - Define o diretório onde o arquivo de saída será gerado.
    * Exemplo - importFilePath="C:/Users/MeuUsuario/Music"
    * Padrão - diretório atual do app ("./")
* isSeparateFilesByPlaylist - Define se os arquivos de saída seram separados por playlist ao invés de estarem todas no mesmo arquivo.
    * Exemplo - isSeparateFilesByPlaylist="true"
    * Padrão - false
    * Obs: Sobrescreve o 'saveWithName'
* playlistTracksPerFile - Define a quantidade máxima de músicas exportadas de cada playlist por arquivo. útil para exportar playlist para algum app de streaming (através do [Soundiiz](https://soundiiz.com/pt/) por exemplo, cuja versão Free só aceita 200 músicas por export). 
    * Exemplo - playlistTracksPerFile="200"
    * Padrão - todas (sem valor)

Parâmetros sem valor ('saveAs=', por exemplo) serão considerados o padrão.\
Prefira sempre passar os parâmetros dentro de aspas "".
