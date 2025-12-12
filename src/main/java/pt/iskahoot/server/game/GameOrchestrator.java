package pt.iskahoot.server.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.iskahoot.common.model.Question;
import pt.iskahoot.common.model.QuestionType;
import pt.iskahoot.common.model.Team;
import pt.iskahoot.common.net.Message;
import pt.iskahoot.common.net.MessageTypes;
import pt.iskahoot.server.coordination.Barrier;
import pt.iskahoot.server.coordination.ModifiedCountDownLatch;

import java.io.IOException;
import java.util.Map;

/**
 * Orquestra o ciclo de jogo: envia perguntas, aguarda respostas,
 * calcula pontuações e gere o fluxo das rondas.
 */
public class GameOrchestrator {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameOrchestrator.class);
    private static final int DEFAULT_QUESTION_TIMEOUT = 30; // segundos

    private final GameState gameState;

    public GameOrchestrator(GameState gameState) {
        this.gameState = gameState;
    }

    /**
     * Inicia o ciclo de jogo.
     */
    public void startGame() {
        LOGGER.info("Starting game {}", gameState.code());
        gameState.setStatus(GameState.GameStatus.IN_PROGRESS);
        
        // Notificar jogadores que o jogo começou
        broadcastGameStart();
        
        // Ciclo de perguntas
        while (gameState.hasMoreQuestions()) {
            gameState.nextQuestion();
            runQuestionRound();
        }
        
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
    }

    private void runQuestionRound() {
        Question question = gameState.getCurrentQuestion();
        if (question == null) {
            return;
        }

        LOGGER.info("Starting round {} with question: {}", 
            gameState.getCurrentQuestionIndex() + 1, question.prompt());

        // Enviar pergunta a todos os jogadores
        broadcastQuestion(question);

        // Configurar mecanismo de coordenação baseado no tipo de pergunta
        if (question.type() == QuestionType.INDIVIDUAL) {
            handleIndividualQuestion(question);
        } else {
            handleTeamQuestion(question);
        }

        // Calcular e aplicar pontuações
        gameState.calculateAndApplyScores();

        // Enviar resumo da ronda
        broadcastRoundEnd();

        // Pequena pausa entre rondas
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void handleIndividualQuestion(Question question) {
        int totalPlayers = gameState.registeredPlayers();
        
        // Criar CountDownLatch modificado
        // bonusFactor=2 para os primeiros 2 jogadores, wait=30s
        ModifiedCountDownLatch latch = new ModifiedCountDownLatch(
            2, 2, DEFAULT_QUESTION_TIMEOUT, totalPlayers
        );
        gameState.setCurrentCountDownLatch(latch);

        try {
            // Aguardar até que todos respondam ou tempo expire
            latch.await();
        } catch (InterruptedException e) {
            LOGGER.warn("Question round interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

    private void handleTeamQuestion(Question question) {
        Map<String, Team> teams = gameState.getAllTeams();
        gameState.clearTeamBarriers();

        // Criar barreira para cada equipa
        for (Team team : teams.values()) {
            Barrier barrier = new Barrier(team.size());
            gameState.setTeamBarrier(team.name(), barrier);
        }

        // Aguardar com timeout
        long timeoutMs = DEFAULT_QUESTION_TIMEOUT * 1000L;
        try {
            Thread.sleep(timeoutMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Marcar todas as barreiras como expiradas
        for (Team team : teams.values()) {
            Barrier barrier = gameState.getTeamBarrier(team.name());
            if (barrier != null) {
                barrier.expire();
            }
        }
    }

    private void broadcastGameStart() {
        JsonObject payload = new JsonObject();
        payload.addProperty("gameCode", gameState.code());
        payload.addProperty("totalQuestions", gameState.questions().size());
        Message message = new Message(MessageTypes.GAME_START, payload);
        broadcast(message);
    }

    private void broadcastQuestion(Question question) {
        JsonObject payload = new JsonObject();
        payload.addProperty("questionNumber", gameState.getCurrentQuestionIndex() + 1);
        payload.addProperty("totalQuestions", gameState.questions().size());
        payload.addProperty("prompt", question.prompt());
        payload.addProperty("points", question.points());
        payload.addProperty("type", question.type().toString());
        payload.addProperty("timeLimit", DEFAULT_QUESTION_TIMEOUT);
        
        JsonArray options = new JsonArray();
        for (String option : question.options()) {
            options.add(option);
        }
        payload.add("options", options);
        
        Message message = new Message(MessageTypes.QUESTION, payload);
        broadcast(message);
    }

    private void broadcastRoundEnd() {
        Question question = gameState.getCurrentQuestion();
        
        JsonObject payload = new JsonObject();
        payload.addProperty("questionNumber", gameState.getCurrentQuestionIndex() + 1);
        payload.addProperty("correctAnswer", question.correctIndex());
        payload.addProperty("correctOption", question.options().get(question.correctIndex()));
        
        // Adicionar placar atualizado
        JsonArray leaderboard = new JsonArray();
        for (GameState.TeamSnapshot snapshot : gameState.snapshotTeams()) {
            JsonObject teamObj = new JsonObject();
            teamObj.addProperty("name", snapshot.name());
            teamObj.addProperty("players", snapshot.players());
            teamObj.addProperty("score", snapshot.score());
            leaderboard.add(teamObj);
        }
        payload.add("leaderboard", leaderboard);
        
        Message message = new Message(MessageTypes.ROUND_END, payload);
        broadcast(message);
    }

    private void broadcastGameEnd() {
        JsonObject payload = new JsonObject();
        
        // Classificação final
        JsonArray finalLeaderboard = new JsonArray();
        for (GameState.TeamSnapshot snapshot : gameState.snapshotTeams()) {
            JsonObject teamObj = new JsonObject();
            teamObj.addProperty("name", snapshot.name());
            teamObj.addProperty("players", snapshot.players());
            teamObj.addProperty("score", snapshot.score());
            finalLeaderboard.add(teamObj);
        }
        payload.add("finalLeaderboard", finalLeaderboard);
        
        Message message = new Message(MessageTypes.GAME_END, payload);
        broadcast(message);
    }

    private void broadcast(Message message) {
        Map<String, GameState.PlayerConnection> connections = gameState.getPlayerConnections();
        for (GameState.PlayerConnection conn : connections.values()) {
            try {
                conn.writer().write(message.toJson());
                conn.writer().newLine();
                conn.writer().flush();
            } catch (IOException e) {
                LOGGER.error("Failed to send message to player {}", conn.username(), e);
            }
        }
    }
}

