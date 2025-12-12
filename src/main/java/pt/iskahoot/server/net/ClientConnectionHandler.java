package pt.iskahoot.server.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.iskahoot.common.model.Question;
import pt.iskahoot.common.model.QuestionType;
import pt.iskahoot.common.net.Message;
import pt.iskahoot.common.net.MessageTypes;
import pt.iskahoot.server.coordination.Barrier;
import pt.iskahoot.server.coordination.ModifiedCountDownLatch;
import pt.iskahoot.server.game.GameManager;
import pt.iskahoot.server.game.GameState;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Gere uma ligação individual de cliente durante todo o ciclo de jogo.
 */
public final class ClientConnectionHandler implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientConnectionHandler.class);

    private final Socket socket;
    private final GameManager gameManager;
    private String username;
    private String teamName;
    private GameState gameState;

    public ClientConnectionHandler(Socket socket, GameManager gameManager) {
        this.socket = Objects.requireNonNull(socket, "socket must not be null");
        this.gameManager = Objects.requireNonNull(gameManager, "gameManager must not be null");
    }

    @Override
    public void run() {
        try (socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {

            // Inicialmente envio um resumo do servidor
            sendServerInfo(writer);

            String line = reader.readLine();
            if (line == null) {
                return;
            }

            Message message = Message.fromJson(line);
            if (!MessageTypes.JOIN_REQUEST.equals(message.type())) {
                LOGGER.warn("Unexpected message type {} from {}", message.type(), socket.getRemoteSocketAddress());
                writer.write(Message.of(MessageTypes.JOIN_REJECTED).toJson());
                writer.newLine();
                writer.flush();
                return;
            }

            if (!hasRequiredFields(message)) {
                sendJoinRejected(writer, "Malformed join request");
                return;
            }

            String gameCode = message.payload().get("gameCode").getAsString().trim();
            this.teamName = message.payload().get("teamName").getAsString().trim();
            this.username = message.payload().get("username").getAsString().trim();

            if (gameCode.isEmpty() || teamName.isEmpty() || username.isEmpty()) {
                sendJoinRejected(writer, "Join request fields must not be blank");
                return;
            }

            var gameOpt = gameManager.findGame(gameCode);
            if (gameOpt.isEmpty()) {
                sendJoinRejected(writer, "Unknown game code: " + gameCode);
                return;
            }

            this.gameState = gameOpt.get();
            GameState.RegistrationResult result = gameState.registerPlayer(teamName, username);
            if (!result.accepted()) {
                sendJoinRejected(writer, result.message());
                return;
            }

            // Registar a conexão do jogador para broadcast de mensagens
            gameState.registerPlayerConnection(username, writer);

            // Resposta positiva inclui fotografia das equipas já registadas
            sendJoinAccepted(writer, gameState);
            LOGGER.info("Player {} joined game {} (team {})", username, gameCode, teamName);

            // Manter conexão ativa e processar respostas durante o jogo
            handleGameLoop(reader, writer);
            
        } catch (IOException ex) {
            LOGGER.error("Error handling client connection for user {}", username, ex);
        } finally {
            LOGGER.info("Connection closed for player {}", username);
        }
    }

    /**
     * Loop principal para processar mensagens do cliente durante o jogo.
     */
    private void handleGameLoop(BufferedReader reader, BufferedWriter writer) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            try {
                Message message = Message.fromJson(line);
                
                if (MessageTypes.ANSWER.equals(message.type())) {
                    handleAnswer(message);
                } else {
                    LOGGER.warn("Unexpected message type during game: {}", message.type());
                }
                
                // Verificar se o jogo terminou
                if (gameState.getStatus() == GameState.GameStatus.FINISHED) {
                    LOGGER.info("Game finished, closing connection for player {}", username);
                    break;
                }
            } catch (Exception e) {
                LOGGER.error("Error processing message from player {}", username, e);
                // Se houver erro crítico, sair do loop
                break;
            }
        }
    }

    /**
     * Processa a resposta de um jogador.
     */
    private void handleAnswer(Message message) {
        if (!message.payload().has("answerIndex")) {
            LOGGER.warn("Answer message missing answerIndex from player {}", username);
            return;
        }

        int answerIndex = message.payload().get("answerIndex").getAsInt();
        long responseTime = message.payload().has("responseTime") 
            ? message.payload().get("responseTime").getAsLong() 
            : 0;

        LOGGER.info("Player {} answered {} (response time: {}ms)", username, answerIndex, responseTime);

        // Coordenar com o mecanismo apropriado
        Question currentQuestion = gameState.getCurrentQuestion();
        if (currentQuestion == null) {
            return;
        }

        int bonusFactor = 1; // Por defeito, sem bónus

        if (currentQuestion.type() == QuestionType.INDIVIDUAL) {
            // Perguntas individuais: decrementar CountDownLatch e obter bonusFactor
            ModifiedCountDownLatch latch = gameState.getCurrentCountDownLatch();
            if (latch != null) {
                bonusFactor = latch.countDown();
                LOGGER.debug("Player {} bonus factor: {}", username, bonusFactor);
            }
        } else {
            // Perguntas de equipa: notificar barreira da equipa
            Barrier barrier = gameState.getTeamBarrier(teamName);
            if (barrier != null) {
                boolean isLast = barrier.barrierAction();
                if (isLast) {
                    LOGGER.info("Team {} completed answering", teamName);
                }
            }
        }

        // Registar a resposta no estado do jogo com o bonusFactor
        gameState.recordAnswer(username, answerIndex, responseTime, bonusFactor);
    }

    private boolean hasRequiredFields(Message message) {
        return message.payload().has("gameCode")
            && message.payload().has("teamName")
            && message.payload().has("username");
    }

    private void sendServerInfo(BufferedWriter writer) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("message", "IsKahoot server ready");
        payload.add("games", describeGames());
        Message envelope = new Message(MessageTypes.SERVER_INFO, payload);
        writer.write(envelope.toJson());
        writer.newLine();
        writer.flush();
    }

    private JsonArray describeGames() {
        JsonArray array = new JsonArray();
        for (GameManager.GameDescriptor descriptor : gameManager.listGames()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("code", descriptor.code());
            entry.addProperty("teams", descriptor.configuration().teamCount());
            entry.addProperty("playersPerTeam", descriptor.configuration().playersPerTeam());
            entry.addProperty("registeredPlayers", descriptor.registeredPlayers());
            array.add(entry);
        }
        return array;
    }

    private void sendJoinAccepted(BufferedWriter writer, GameState gameState) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("gameCode", gameState.code());
        payload.add("teams", describeTeamSnapshot(gameState));
        payload.addProperty("questions", gameState.questions().size());
        Message message = new Message(MessageTypes.JOIN_ACCEPTED, payload);
        writer.write(message.toJson());
        writer.newLine();
        writer.flush();
    }

    private JsonArray describeTeamSnapshot(GameState gameState) {
        JsonArray array = new JsonArray();
        for (GameState.TeamSnapshot snapshot : gameState.snapshotTeams()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", snapshot.name());
            entry.addProperty("players", snapshot.players());
            entry.addProperty("score", snapshot.score());
            array.add(entry);
        }
        return array;
    }

    private void sendJoinRejected(BufferedWriter writer, String reason) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("reason", reason);
        Message message = new Message(MessageTypes.JOIN_REJECTED, payload);
        writer.write(message.toJson());
        writer.newLine();
        writer.flush();
    }
}
