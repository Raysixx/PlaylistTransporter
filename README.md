# DeezerPlaylistImporter

App para importar playlists do Deezer e exportá-las em um arquivo.

## Como funciona:
O app cria um server local temporário e então abre uma tela de autenticação do Deezer. Após a autenticação sucedida, o usuário é redirecionado para a URL do server local temporário. Um *código da Deezer* é embutido junto nessa URL, o app pega esse código e o redireciona para uma outra URL que retorna um *token* temporário da Deezer caso o código sejá válido, esse token permite que o app converse com os servidores da Deezer atráves de uma API e consiga os dados de playlists e músicas. Por final, é criado um arquivo com esses dados para o usuário.

Processo oficial descrito em: https://developers.deezer.com/api/oauth

## Extensões de arquivos de saída atualmente suportados:
* txt
* csv

## Como usar:

Baixe o zip da última release desse projeto, extraia, rode o DeezerPlaylistImporter.jar (ou o import.bat caso esteja setando algum parâmetro), logue com sua conta da Deezer e pronto.

Por padrão, o app exportará todas as playlists com todas as músicas em um único arquivo 'Playlists.txt' no mesmo diretório do jar.

Você pode editar o import.bat para setar algum desses parâmetros abaixo, mas então precisaria executar o app rodando esse import.bat.\
Obs: Mantenha o ^ no final das linhas.\
Obs: O import.bat precisa estar na mesma pasta do DeezerPlaylistImporter.jar

### Parâmetros Opcionais:
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
* playlistToImport - Define uma playlist específica para ser importada através do Nome.
    * Exemplo - playlistToImport="Loved Tracks"
    * Padrão - todas (sem valor)

Parâmetros sem valor ('saveAs=', por exemplo) serão considerados o padrão.\
Prefira sempre passar os parâmetros dentro de aspas "".
