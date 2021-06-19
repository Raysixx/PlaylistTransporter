# DeezerPlaylistImporter

App para importar playlists do Deezer e exportá-las em um arquivo.

## Como funciona:
O app cria um server local temporário e então abre uma tela de autenticação do Deezer. Após a autenticação sucedida, o usuário é redirecionado para a URL do server local temporário. Um *código da Deezer* é embutido junto nessa URL, o app pega esse código e o redireciona para uma outra URL que retorna um *token* temporário da Deezer caso o código sejá válido, esse token permite que o app converse com os servidores da Deezer atráves de uma API e consiga os dados de playlists e músicas. Por final, é criado um arquivo com esses dados para o usuário.

Processo oficial descrito em: https://developers.deezer.com/api/oauth

## Como usar:
### Parâmetros Opcionais:
* saveWithName - Define o nome do arquivo de saída com as playlists e músicas (padrão 'tracks')
* saveAs - Define a extensão do arquivo de saída (padrão 'txt')
* importFilePath - Define o diretório onde o arquivo de saída será gerado (padrão diretório atual do app)
* playlistToImport - Define uma playlist específica para ser importada através do Nome (padrão todas)

Baixe o zip que está na release desse projeto, extraia na mesma pasta, rode o import.bat, logue com sua conta da Deezer e pronto. 

Você pode editar o import.bat para setar algum desses parâmetros acima.
