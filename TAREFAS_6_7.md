# Tarefas 6 e 7 - Processamento de Respostas e Coordenação Final

## ✅ Tarefa 6: Processamento das Respostas

### Implementações Realizadas

#### 1. Sistema de Bonificação com CountDownLatch
**Ficheiro**: `GameState.java`

- **Record `PlayerAnswer` atualizado** com campo `bonusFactor`:
  ```java
  public record PlayerAnswer(String username, int answerIndex, long responseTimeMs, int bonusFactor)
  ```

- **Aplicação correta do bonusFactor**:
  - O CountDownLatch retorna o fator de bónus quando `countDown()` é chamado
  - Os primeiros `bonusCount` jogadores recebem `bonusFactor` (configurado para 2x)
  - Restantes jogadores recebem fator 1 (pontuação base)

**Ficheiro**: `ClientConnectionHandler.java`

- **Coordenação correta**: O bonusFactor é obtido do CountDownLatch **antes** de registar a resposta
- A resposta é registada com o bonusFactor correspondente à ordem de chegada

#### 2. Cálculo de Pontos Melhorado

##### Perguntas Individuais (`INDIVIDUAL`)
**Ficheiro**: `GameState.java` - método `calculateIndividualScores()`

```java
int pointsToAdd = basePoints * answer.bonusFactor();
team.addScore(pointsToAdd);
```

- Cada jogador que acerta recebe pontos = `pontos_base * bonusFactor`
- Os primeiros 2 jogadores recebem 2x os pontos
- Restantes recebem 1x os pontos

##### Perguntas de Equipa (`TEAM`)
**Ficheiro**: `GameState.java` - método `calculateTeamScores()`

Lógica implementada:
1. **Todos responderam e todos acertaram**: `pontos_base * 2` (pontuação duplicada)
2. **Pelo menos alguém acertou**: `pontos_base * 1` (sem bonificação extra)
3. **Ninguém acertou**: `0 pontos`

Regras:
- A equipa só recebe bonificação máxima se **todos os membros** responderem **corretamente**
- Se nem todos responderem ou nem todos acertarem, recebe pontuação simples
- Implementa o conceito de "consenso" da equipa

## ✅ Tarefa 7: Desenvolvimento do Ciclo e Fim do Jogo

### Implementações Realizadas

#### 1. Coordenação de Fim de Jogo
**Ficheiro**: `ClientConnectionHandler.java` - método `handleGameLoop()`

```java
// Verificar se o jogo terminou
if (gameState.getStatus() == GameState.GameStatus.FINISHED) {
    LOGGER.info("Game finished, closing connection for player {}", username);
    break;
}
```

- Loop de jogo verifica constantemente o estado do jogo
- Quando o jogo termina (`FINISHED`), a thread do cliente sai do loop gracefully
- Conexões são fechadas automaticamente com try-with-resources

#### 2. Sincronização no Fim do Jogo
**Ficheiro**: `GameOrchestrator.java`

```java
// Fim do jogo
gameState.setStatus(GameState.GameStatus.FINISHED);
broadcastGameEnd();
LOGGER.info("Game {} finished", gameState.code());

// Dar tempo para os clientes processarem a mensagem de fim de jogo
try {
    Thread.sleep(2000);
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
}
```

- Garante que a mensagem `GAME_END` é enviada antes de terminar
- Pausa de 2 segundos para clientes processarem a mensagem final
- Tratamento correto de InterruptedException

#### 3. Validação de Jogadores ao Iniciar
**Ficheiro**: `ServerCli.java` - método `startGame()`

```java
// Verificar se há jogadores suficientes
int expectedPlayers = gameState.configuration().teamCount() * 
                    gameState.configuration().playersPerTeam();
int currentPlayers = gameState.registeredPlayers();

if (currentPlayers < expectedPlayers) {
    System.out.printf("Aviso: Jogo configurado para %d jogadores mas apenas %d estão conectados.%n",
        expectedPlayers, currentPlayers);
    System.out.print("Deseja iniciar mesmo assim? (s/n): ");
    // ... confirmação do utilizador
}
```

- Valida número de jogadores esperado vs conectados
- Permite iniciar jogo com menos jogadores mediante confirmação
- Previne erros de coordenação por falta de jogadores

#### 4. Classificação Final Ordenada
**Ficheiro**: `IsKahootClient.java` - método `handleGameEnd()`

```java
// Ordenar equipas por pontuação (descendente)
List<JsonObject> teams = new ArrayList<>();
for (int i = 0; i < finalLeaderboard.size(); i++) {
    teams.add(finalLeaderboard.get(i).getAsJsonObject());
}
teams.sort((a, b) -> b.get("score").getAsInt() - a.get("score").getAsInt());
```

- Classificação final ordenada por pontuação (maior para menor)
- Medalhas atribuídas corretamente (🥇🥈🥉)
- Apresentação visual clara do vencedor

## 🎯 Requisitos Cumpridos

### Tarefa 6 - Processamento de Respostas
- ✅ **CountDownLatch aplicado** nas perguntas individuais
- ✅ **Barreira aplicada** nas perguntas de equipa
- ✅ **Bonificação para primeiros** 2 jogadores (configurável)
- ✅ **Contabilização correta** de pontos por tipo de pergunta
- ✅ **Tabela de pontuações** atualizada após cada ronda

### Tarefa 7 - Ciclo e Fim do Jogo
- ✅ **Ciclo completo** de perguntas implementado
- ✅ **Encadeamento de rondas** até fim das perguntas
- ✅ **Fim do jogo** quando todas as perguntas foram respondidas
- ✅ **Interrupção de threads** de clientes quando jogo termina
- ✅ **Threads locais de jogo** terminadas corretamente
- ✅ **Classificação final** enviada a todos os participantes

## 🔄 Fluxo Completo do Jogo

1. **Criação**: `new 2 2 3` → cria `game0`
2. **Conexão**: 4 clientes conectam-se às equipas
3. **Validação**: Servidor verifica jogadores suficientes
4. **Início**: `start game0` → `GameOrchestrator` inicia
5. **Para cada pergunta**:
   - Servidor envia `QUESTION`
   - Clientes exibem e aguardam resposta
   - Clientes enviam `ANSWER`
   - **Se INDIVIDUAL**: CountDownLatch coordena, primeiros recebem bónus
   - **Se TEAM**: Barrier coordena, todos devem responder
   - Servidor calcula pontuações com lógica apropriada
   - Servidor envia `ROUND_END` com placar atualizado
6. **Fim**: Após última pergunta:
   - Servidor envia `GAME_END` com classificação final
   - Estado muda para `FINISHED`
   - Threads de clientes verificam estado e terminam
   - Conexões fechadas gracefully

## 📊 Exemplo de Pontuação

### Pergunta Individual (5 pontos base)
- **Alice** responde corretamente em 1º → 5 × 2 = **10 pontos** ✨
- **Bob** responde corretamente em 2º → 5 × 2 = **10 pontos** ✨
- **Carol** responde corretamente em 3º → 5 × 1 = **5 pontos**
- **Dave** responde incorretamente → **0 pontos**

### Pergunta de Equipa (3 pontos base)
**TeamA** (Alice + Bob):
- Ambos respondem corretamente → 3 × 2 = **6 pontos** 🎉

**TeamB** (Carol + Dave):
- Carol acerta, Dave erra → 3 × 1 = **3 pontos**

## 🎓 Conformidade com Enunciado

### Seção 2.4 - Ciclo de Perguntas
✅ Pergunta enviada com identificação e opções  
✅ Cronómetro decrescente (30s)  
✅ Todas as perguntas são ficheiro JSON (Gson)  

### Seção 2.5 - Receção de Respostas
✅ Respostas registadas atomicamente (synchronized)  
✅ Interferências tratadas com coordenação apropriada  
✅ Timeout de ronda implementado  
✅ Placar enviado após cada ronda  

### Seção 2.6 - Coordenação para Fim do Jogo
✅ Jogo termina quando todas as perguntas são respondidas  
✅ Threads dos jogadores interrompidas (`DealWithClient`)  
✅ Estado verificado em loop  

### Seção 2.7 - Tipos de Perguntas
✅ **Individuais**: CountDownLatch com bonusFactor  
✅ **Equipas**: Barrier com consenso  

## 🔧 Melhorias Técnicas

1. **Thread Safety**: Todos os métodos de coordenação são thread-safe
2. **Resource Management**: Try-with-resources garante fechamento de conexões
3. **Error Handling**: Exceções capturadas e logadas apropriadamente
4. **Graceful Shutdown**: Threads terminam limpa e previsívelmente
5. **User Feedback**: Validações e confirmações no servidor e cliente

## 📝 Testes Recomendados

1. **Teste Básico**: 2 equipas, 2 jogadores, 3 perguntas
2. **Teste de Bonificação**: Verificar primeiros jogadores recebem 2x
3. **Teste de Equipa**: Verificar consenso (todos acertam = 2x pontos)
4. **Teste de Timeout**: Não responder e verificar que ronda avança
5. **Teste de Fim**: Verificar classificação final ordenada
6. **Teste de Interrupção**: Verificar threads terminam após GAME_END

## ✅ Status Final

🎉 **Tarefas 6 e 7 Completas!**

- ✅ Compilação sem erros
- ✅ Sem warnings de linting
- ✅ Todos os requisitos do enunciado implementados
- ✅ Coordenação própria (sem java.util.concurrent)
- ✅ Bonificações aplicadas corretamente
- ✅ Fim de jogo coordenado
- ✅ Pronto para teste e demonstração!

