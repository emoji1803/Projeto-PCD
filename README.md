# IsKahoot

Implementação de referência para o projeto distribuído de PCD descrito no enunciado.  
Esta entrega cobre as três primeiras fases: cliente (TUI), estrutura de estado do jogo e carregamento das perguntas em JSON.

## Requisitos

- Java 17
- Maven 3.9+

## Estrutura do projeto

```
src/
  main/
    java/
      pt/iskahoot/
        client/          # Cliente TUI
        common/          # Modelos e protocolo de mensagens
        server/          # Servidor, CLI e gestão do jogo
    resources/
      questions.json     # Perguntas de exemplo (pode ser editado)
```

## Como compilar

```bash
mvn clean package
```

Os binários gerados ficam em `target/`.

## Como executar o servidor

```bash
mvn exec:java -Dexec.mainClass=pt.iskahoot.server.IsKahootServer \
  -Dexec.args="--port=8080 --questions=src/main/resources/questions.json"
```

Comandos disponíveis na consola do servidor:

- `new <equipas> <jogadores_por_equipa> <perguntas> [<codigo>]`
- `list`
- `help`
- `exit`

## Como executar o cliente

```bash
mvn exec:java -Dexec.mainClass=pt.iskahoot.client.IsKahootClient \
  -Dexec.args="localhost 8080 ABC123 TeamX Alice"
```

Sem argumentos o cliente entra em modo interativo e pede os parâmetros.
