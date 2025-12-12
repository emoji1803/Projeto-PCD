# IsKahoot

Implementação do projeto distribuído de PCD - Jogo concorrente e distribuído inspirado no Kahoot!

Esta implementação cobre as fases 1-7 do enunciado:
- **Fase 1**: Cliente TUI para interação com o jogo
- **Fase 2**: Estrutura GameState para gestão do estado do jogo
- **Fase 3**: Carregamento de perguntas em formato JSON
- **Fase 4**: Servidor e ligação inicial dos clientes
- **Fase 5**: Troca de mensagens entre clientes e servidor, ciclo de jogo completo
- **Fase 6**: Processamento de respostas com CountDownLatch e Barrier
- **Fase 7**: Desenvolvimento do ciclo completo e coordenação de fim de jogo

## Destaques da Implementação

### Mecanismos de Coordenação Próprios
Conforme requisito do enunciado, **todos os mecanismos de coordenação foram desenvolvidos pelo próprio grupo**:
- `ModifiedCountDownLatch`: Implementação própria para perguntas individuais com suporte a bonusFactor e bonusCount
- `Barrier`: Implementação própria usando variáveis condicionais (wait/notify) para perguntas de equipa

### Códigos Sequenciais
Os jogos agora recebem códigos sequenciais (`game0`, `game1`, `game2`, ...) em vez de códigos aleatórios, facilitando a gestão e debug.

## Requisitos Obrigatórios Implementados

✅ **Mecanismos de coordenação desenvolvidos pelo próprio grupo** (não usa bibliotecas padrão do Java)
- `ModifiedCountDownLatch` com wait/notify
- `Barrier` com variáveis condicionais

✅ **Dois tipos de perguntas**:
- Individuais com bonificação para os primeiros (2x pontos)
- De equipa com coordenação por barreira (2x pontos se todos acertarem)

✅ **Protocolo de mensagens JSON** entre cliente e servidor

✅ **Threads dedicadas** para cada cliente (DealWithClient)

✅ **Gestão de estado do jogo** em `GameState`

✅ **Evita uso excessivo de static** (apenas onde necessário, como constantes e métodos utilitários)

✅ **Processamento de respostas** (Tarefa 6):
- CountDownLatch aplicado em perguntas individuais
- Barrier aplicado em perguntas de equipa
- Bonificação correta para primeiros jogadores
- Contabilização de pontos por tipo de pergunta

✅ **Ciclo e fim de jogo** (Tarefa 7):
- Ciclo completo de perguntas até ao fim
- Interrupção correta de threads ao terminar
- Classificação final ordenada
- Coordenação de encerramento graceful

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

- `new <equipas> <jogadores_por_equipa> <perguntas>` - Cria um novo jogo
- `start <codigo_jogo>` - Inicia o jogo (ex: `start game0`)
- `list` - Lista jogos ativos
- `help` - Mostra ajuda
- `exit` - Termina o servidor

### Exemplo de Uso

1. Criar um jogo com 2 equipas, 2 jogadores por equipa, 3 perguntas:
   ```
   new 2 2 3
   ```
   Output: `Jogo criado com código game0`

2. Conectar clientes (ver abaixo)

3. Iniciar o jogo quando todos os jogadores estiverem conectados:
   ```
   start game0
   ```

## Como executar o cliente

### Cliente Linha de Comando
```bash
mvn exec:java -Dexec.mainClass=pt.iskahoot.client.IsKahootClient \
  -Dexec.args="localhost 8080 game0 TeamA Alice"
```

### Cliente GUI Swing (Recomendado) ⭐
```bash
mvn exec:java -Dexec.mainClass=pt.iskahoot.client.IsKahootImprovedSwingClient \
  -Dexec.args="localhost 8080 game0 TeamA Alice"
```

Ou sem argumentos para diálogo interativo:
```bash
mvn exec:java -Dexec.mainClass=pt.iskahoot.client.IsKahootImprovedSwingClient
```

**Funcionalidades da GUI:**
- ✨ Placar lateral sempre visível
- ✨ Perguntas com opções em radio buttons
- ✨ Resposta correta destacada a verde após cada ronda
- ✨ Destaque visual da tua equipa no placar (★)
- ✨ Log de histórico em tempo real

### Exemplo Completo de Jogo

1. **Terminal 1 - Servidor:**
   ```bash
   mvn exec:java -Dexec.mainClass=pt.iskahoot.server.IsKahootServer
   ```
   No prompt do servidor:
   ```
   iskahoot> new 2 2 3
   Jogo criado com código game0
   ```

2. **Terminal 2 - Cliente 1:**
   ```bash
   mvn exec:java -Dexec.mainClass=pt.iskahoot.client.IsKahootClient \
     -Dexec.args="localhost 8080 game0 TeamA Alice"
   ```

3. **Terminal 3 - Cliente 2:**
   ```bash
   mvn exec:java -Dexec.mainClass=pt.iskahoot.client.IsKahootClient \
     -Dexec.args="localhost 8080 game0 TeamA Bob"
   ```

4. **Terminal 4 - Cliente 3:**
   ```bash
   mvn exec:java -Dexec.mainClass=pt.iskahoot.client.IsKahootClient \
     -Dexec.args="localhost 8080 game0 TeamB Carol"
   ```

5. **Terminal 5 - Cliente 4:**
   ```bash
   mvn exec:java -Dexec.mainClass=pt.iskahoot.client.IsKahootClient \
     -Dexec.args="localhost 8080 game0 TeamB Dave"
   ```

6. **De volta ao Terminal 1 - Servidor:**
   ```
   iskahoot> start game0
   Jogo iniciado!
   ```

Agora todos os clientes receberão as perguntas sequencialmente, poderão responder, e verão os resultados!

## Arquitetura e Funcionamento

### Tipos de Perguntas

O jogo suporta dois tipos de perguntas conforme o enunciado:

#### Perguntas Individuais (`INDIVIDUAL`)
- Cada jogador responde independentemente
- Coordenação via `ModifiedCountDownLatch` próprio
- Os primeiros `bonusCount` jogadores recebem pontuação multiplicada por `bonusFactor`
- Exemplo no JSON: `"type": "INDIVIDUAL"`

#### Perguntas de Equipa (`TEAM`)
- Todos os membros da equipa devem responder
- Coordenação via `Barrier` próprio (com variáveis condicionais)
- Pontuação baseada no consenso/melhor resposta da equipa
- Exemplo no JSON: `"type": "TEAM"`

### Ciclo de Jogo

1. **Criação do Jogo**: Servidor cria jogo com código sequencial
2. **Registo de Jogadores**: Clientes conectam-se e juntam-se a equipas
3. **Início do Jogo**: Comando `start` inicia o ciclo de perguntas
4. **Para cada Pergunta**:
   - Servidor envia pergunta a todos os jogadores
   - Inicia cronómetro decrescente (30s por defeito)
   - Jogadores enviam respostas
   - Coordenação apropriada (CountDownLatch ou Barrier)
   - Cálculo de pontuações
   - Envio de placar atualizado
5. **Fim do Jogo**: Classificação final enviada a todos

### Estrutura de Classes Principais

- **`GameManager`**: Gestão de jogos ativos, códigos sequenciais
- **`GameState`**: Estado mutável de um jogo (jogadores, equipas, pontuações, ronda atual)
- **`GameOrchestrator`**: Orquestração do ciclo de jogo (envio de perguntas, coordenação)
- **`ClientConnectionHandler`**: Gestão de conexão de um cliente (handshake + loop de jogo)
- **`ModifiedCountDownLatch`**: Mecanismo próprio de coordenação para perguntas individuais
- **`Barrier`**: Mecanismo próprio de coordenação para perguntas de equipa
