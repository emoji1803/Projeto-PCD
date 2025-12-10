# Alterações Implementadas - Tarefas 4 e 5

## Resumo
Este documento descreve as alterações implementadas para completar as tarefas 4 e 5 do projeto IsKahoot, incluindo as sugestões do professor.

## 1. Mecanismos de Coordenação Próprios

### ModifiedCountDownLatch
**Ficheiro**: `src/main/java/pt/iskahoot/server/coordination/ModifiedCountDownLatch.java`

Implementação própria de CountDownLatch conforme especificado no enunciado:
- **Não usa** `java.util.concurrent.CountDownLatch`
- Implementado com `synchronized`, `wait()` e `notify()` - mecanismos básicos de Java
- Parâmetros:
  - `bonusFactor`: multiplicador de pontos para os primeiros jogadores
  - `bonusCount`: quantos jogadores recebem bónus
  - `waitPeriod`: período de espera em segundos
  - `count`: número de respostas esperadas
- Método `countDown()` retorna o fator de bónus para cada jogador
- Método `await()` bloqueia até todas as respostas chegarem ou tempo expirar

### Barrier
**Ficheiro**: `src/main/java/pt/iskahoot/server/coordination/Barrier.java`

Implementação própria de barreira com variáveis condicionais:
- **Não usa** bibliotecas de coordenação do Java
- Implementado com `synchronized`, `wait()` e `notify()` - variáveis condicionais básicas
- Usado para perguntas de equipa
- Coordena quando todos os membros da equipa responderam
- Suporta timeout com `await(long timeoutMillis)`

## 2. Códigos Sequenciais (Sugestão do Professor)

### GameManager
**Ficheiro**: `src/main/java/pt/iskahoot/server/game/GameManager.java`

Alterações:
- ❌ Removido: `SecureRandom` e geração de códigos aleatórios
- ✅ Adicionado: `AtomicInteger gameCounter` para IDs sequenciais
- ✅ Adicionado: `List<GameState> gamesInOrder` para lista ordenada
- Códigos agora seguem o padrão: `game0`, `game1`, `game2`, etc.
- Método `getGamesInOrder()` retorna lista ordenada de GameStates

## 3. Tipos de Mensagens Expandidos

### MessageTypes
**Ficheiro**: `src/main/java/pt/iskahoot/common/net/MessageTypes.java`

Novos tipos de mensagem adicionados:
- `GAME_START`: Notifica início do jogo
- `QUESTION`: Envia pergunta aos jogadores
- `ANSWER`: Jogador envia resposta
- `ROUND_END`: Servidor envia resultado da ronda
- `GAME_END`: Servidor envia classificação final
- `WAITING_FOR_PLAYERS`: Informa que aguarda mais jogadores

## 4. Estado do Jogo Expandido

### GameState
**Ficheiro**: `src/main/java/pt/iskahoot/server/game/GameState.java`

Novos campos e funcionalidades:
- `GameStatus status`: Estado do jogo (WAITING, IN_PROGRESS, FINISHED)
- `int currentQuestionIndex`: Índice da pergunta atual
- `Map<String, PlayerConnection> playerConnections`: Conexões ativas dos jogadores
- `Map<String, PlayerAnswer> currentRoundAnswers`: Respostas da ronda atual
- `ModifiedCountDownLatch currentCountDownLatch`: Coordenação para perguntas individuais
- `Map<String, Barrier> currentTeamBarriers`: Barreiras para perguntas de equipa

Novos métodos:
- `registerPlayerConnection()`: Regista conexão de um jogador
- `getCurrentQuestion()`: Retorna pergunta atual
- `nextQuestion()`: Avança para próxima pergunta
- `recordAnswer()`: Regista resposta de um jogador
- `calculateAndApplyScores()`: Calcula pontuações da ronda
- Métodos específicos para coordenação (get/set para latch e barriers)

Records internos:
- `PlayerConnection(String username, BufferedWriter writer)`
- `PlayerAnswer(String username, int answerIndex, long responseTimeMs)`
- `GameStatus` enum

## 5. Orquestração do Jogo

### GameOrchestrator (NOVO)
**Ficheiro**: `src/main/java/pt/iskahoot/server/game/GameOrchestrator.java`

Classe responsável pelo ciclo de jogo:
- `startGame()`: Inicia o ciclo completo do jogo
- `runQuestionRound()`: Executa uma ronda de pergunta
- `handleIndividualQuestion()`: Coordenação para perguntas individuais (CountDownLatch)
- `handleTeamQuestion()`: Coordenação para perguntas de equipa (Barriers)
- `broadcastQuestion()`: Envia pergunta a todos os jogadores
- `broadcastRoundEnd()`: Envia resultado da ronda
- `broadcastGameEnd()`: Envia classificação final

## 6. Gestão de Conexões do Cliente

### ClientConnectionHandler
**Ficheiro**: `src/main/java/pt/iskahoot/server/net/ClientConnectionHandler.java`

Alterações principais:
- ❌ Antes: Fechava conexão após JOIN_ACCEPTED
- ✅ Agora: Mantém conexão ativa durante todo o jogo
- Novo método `handleGameLoop()`: Loop que processa mensagens do cliente
- Novo método `handleAnswer()`: Processa respostas dos jogadores
- Integração com CountDownLatch/Barrier conforme tipo de pergunta
- Thread permanece ativa até fim do jogo ou desconexão

## 7. Cliente Interativo

### IsKahootClient
**Ficheiro**: `src/main/java/pt/iskahoot/client/IsKahootClient.java`

Alterações principais:
- ❌ Antes: Terminava após JOIN_ACCEPTED
- ✅ Agora: Mantém conexão e entra em loop de jogo
- Novo método `gameLoop()`: Processa mensagens do servidor
- Novos handlers:
  - `handleGameStart()`: Exibe início do jogo
  - `handleQuestion()`: Exibe pergunta e lê resposta do utilizador
  - `handleRoundEnd()`: Exibe resultado da ronda e placar
  - `handleGameEnd()`: Exibe classificação final
- Interface de texto melhorada com formatação visual
- Cronómetro e feedback imediato ao jogador

## 8. Interface do Servidor

### ServerCli
**Ficheiro**: `src/main/java/pt/iskahoot/server/tui/ServerCli.java`

Novo comando:
- `start <codigo_jogo>`: Inicia o jogo manualmente
  - Valida se o jogo existe
  - Verifica estado do jogo
  - Inicia GameOrchestrator numa thread separada

## Conformidade com Requisitos

### ✅ Requisitos Obrigatórios Cumpridos

1. **Mecanismos de coordenação próprios**:
   - ModifiedCountDownLatch implementado sem usar java.util.concurrent
   - Barrier implementado com variáveis condicionais básicas (wait/notify)

2. **Dois tipos de perguntas**:
   - INDIVIDUAL: com CountDownLatch e bonificação
   - TEAM: com Barrier e coordenação de equipa

3. **Evitar static**:
   - Uso mínimo de static (apenas constantes em MessageTypes e métodos utilitários)
   - Instâncias geridas adequadamente

4. **Thread por cliente (DealWithClient)**:
   - ClientConnectionHandler implementa Runnable
   - Cada cliente tem thread dedicada que persiste durante o jogo

5. **Protocolo de mensagens**:
   - JSON com type/payload
   - Mensagens bem definidas para cada fase do jogo

6. **Gestão de estado**:
   - GameState mantém estado completo e mutável do jogo
   - Sincronização apropriada com synchronized

## Fluxo de Execução Completo

1. **Servidor inicia** → `IsKahootServer.main()`
2. **Servidor cria jogo** → `ServerCli` comando `new` → `GameManager.createGame()` → código sequencial
3. **Clientes conectam** → `ClientConnectionHandler` por cliente
4. **Handshake** → JOIN_REQUEST/JOIN_ACCEPTED → `GameState.registerPlayer()`
5. **Servidor inicia jogo** → `ServerCli` comando `start` → `GameOrchestrator.startGame()`
6. **Para cada pergunta**:
   - Servidor envia QUESTION
   - Cliente exibe e aguarda resposta do utilizador
   - Cliente envia ANSWER
   - Coordenação (CountDownLatch ou Barrier)
   - Servidor calcula pontuações
   - Servidor envia ROUND_END com placar
7. **Fim do jogo** → Servidor envia GAME_END com classificação final

## Testes Sugeridos

1. Criar jogo: `new 2 2 3`
2. Conectar 4 clientes (2 por equipa)
3. Iniciar jogo: `start game0`
4. Responder às perguntas em cada cliente
5. Verificar:
   - Perguntas INDIVIDUAL: primeiros a responder recebem bónus
   - Perguntas TEAM: equipa precisa que todos respondam
   - Placar atualiza corretamente após cada ronda
   - Classificação final exibida no fim

## Notas de Implementação

- Todas as classes de coordenação usam apenas mecanismos básicos de Java (`synchronized`, `wait`, `notify`)
- Não há dependências de bibliotecas de concorrência além do que já estava no projeto original
- BufferedWriter/BufferedReader para comunicação JSON linha a linha
- Exceções tratadas e logadas apropriadamente com SLF4J
- Código documentado com Javadoc

