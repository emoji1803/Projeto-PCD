# Bug Fix: Race Condition em checkAndStartGameIfReady()

## 🐛 Problema Identificado

### Descrição da Race Condition

No método `checkAndStartGameIfReady()` em `ClientConnectionHandler`, existia uma **race condition crítica** que permitia que múltiplas threads iniciassem o mesmo jogo várias vezes.

### Cenário do Bug

```
Thread A (Cliente 4 conecta):
1. Entra em synchronized(gameState)
2. Verifica: status == WAITING ✓
3. Verifica: 4 jogadores >= 4 esperados ✓
4. Inicia Thread do GameOrchestrator
5. SAI do synchronized block
   ⚠️ STATUS AINDA É WAITING!

Thread B (outro cliente tenta reconectar ou há delay):
1. Entra em synchronized(gameState)
2. Verifica: status == WAITING ✓ (ainda não mudou!)
3. Verifica: 4 jogadores >= 4 esperados ✓
4. Inicia OUTRA Thread do GameOrchestrator ❌
   🔥 DUAS INSTÂNCIAS DO JOGO CORRENDO!
```

### Causa Raiz

O status do jogo só era alterado para `IN_PROGRESS` **dentro** do método `GameOrchestrator.startGame()`, que corre numa thread separada e assíncrona. Entre o momento em que a thread é iniciada e o momento em que o status muda, outras threads podiam entrar no bloco synchronized e ver o status ainda como `WAITING`.

## ✅ Solução Implementada

### Correção Aplicada

**Ficheiro**: `src/main/java/pt/iskahoot/server/net/ClientConnectionHandler.java`

```java
// ANTES (Bug):
if (currentPlayers >= expectedPlayers) {
    LOGGER.info("All players connected to game {}. Starting automatically...", gameState.code());
    
    // Iniciar o jogo numa thread separada
    Thread gameThread = new Thread(() -> {
        // ... GameOrchestrator inicia e DEPOIS muda o status
        orchestrator.startGame(); // <-- status muda AQUI (tarde demais!)
    }, "game-" + gameState.code());
    
    gameThread.start();
}

// DEPOIS (Corrigido):
if (currentPlayers >= expectedPlayers) {
    LOGGER.info("All players connected to game {}. Starting automatically...", gameState.code());
    
    // CRITICAL: Mudar status IMEDIATAMENTE dentro do synchronized block
    gameState.setStatus(GameState.GameStatus.IN_PROGRESS);
    
    // Iniciar o jogo numa thread separada
    Thread gameThread = new Thread(() -> {
        orchestrator.startGame();
    }, "game-" + gameState.code());
    
    gameThread.start();
}
```

### Por Que Funciona

1. ✅ **Status muda DENTRO do bloco synchronized**: Outras threads que entrarem depois verão `status != WAITING` e retornarão imediatamente
2. ✅ **Atómico**: A mudança de status e o início da thread acontecem atomicamente
3. ✅ **Thread-safe**: O `synchronized(gameState)` garante exclusão mútua
4. ✅ **Sem side effects**: O `GameOrchestrator.startGame()` ainda seta o status (idempotente), mas agora é redundante e seguro

## 🔍 Análise de Impacto

### Antes da Correção (Comportamento Incorreto)

- ❌ Possível ter múltiplas threads de orquestração para o mesmo jogo
- ❌ Perguntas enviadas em duplicado
- ❌ Contadores de respostas inconsistentes
- ❌ Pontuações calculadas múltiplas vezes
- ❌ Confusão total no cliente

### Depois da Correção (Comportamento Correto)

- ✅ Apenas UMA thread de orquestração por jogo
- ✅ Perguntas enviadas uma única vez
- ✅ Contadores consistentes
- ✅ Pontuações corretas
- ✅ Experiência de jogo estável

## 🧪 Como Testar

### Teste de Regressão

1. **Criar jogo**: `new 2 2 3` → `game0`
2. **Conectar 4 clientes simultaneamente** (abrir 4 terminais e executar quase ao mesmo tempo)
3. **Observar logs do servidor**:
   - ✅ Deve aparecer apenas UMA mensagem "Starting game game0"
   - ✅ Deve aparecer apenas UM conjunto de perguntas sendo enviadas
4. **Verificar nos clientes**:
   - ✅ Cada pergunta aparece apenas uma vez
   - ✅ Placar atualiza corretamente
   - ✅ Sem mensagens duplicadas

### Teste de Stress

```bash
# Conectar múltiplos clientes em paralelo
for i in {1..4}; do
  mvn exec:java -Dexec.mainClass=pt.iskahoot.client.IsKahootClient \
    -Dexec.args="localhost 8080 game0 Team$i Player$i" &
done
```

Verificar que apenas uma instância do jogo inicia.

## 📝 Commits Relacionados

- `d8c2dfb` - Fix race condition em checkAndStartGameIfReady
- `1910f83` - Inicio automatico do jogo (introduziu o bug)
- `b936da5` - Adicionar IsKahootSwingClient

## 🎓 Lições Aprendidas

### Princípios de Concorrência

1. **Estado deve mudar atomicamente**: Se uma decisão é tomada baseada num estado, a mudança desse estado deve acontecer no mesmo bloco synchronized
2. **Threads assíncronas são perigosas**: Nunca dependa de uma thread assíncrona para mudar estado crítico usado em decisões de controlo de fluxo
3. **Check-then-act é vulnerável**: O padrão "verificar condição → agir" precisa de proteção atómica completa

### Pattern Correto

```java
synchronized (recursoPartilhado) {
    // 1. Verificar condição
    if (condição) {
        // 2. Mudar estado IMEDIATAMENTE
        mudarEstado();
        
        // 3. Iniciar trabalho assíncrono (se necessário)
        iniciarThread();
    }
}
```

## ✅ Status

- ✅ Bug identificado
- ✅ Correção implementada
- ✅ Testes de compilação: PASS
- ✅ Linter: SEM ERROS
- ✅ Commit realizado
- ✅ Push para repositório remoto
- 🔄 Aguardando testes de integração

## 👏 Crédito

Bug identificado por: **Utilizador**  
Análise: Excelente identificação da race condition através de análise de código!

