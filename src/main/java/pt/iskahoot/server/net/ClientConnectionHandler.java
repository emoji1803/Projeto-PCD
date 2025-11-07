package pt.iskahoot.server.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.iskahoot.common.net.Message;
import pt.iskahoot.common.net.MessageTypes;
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
 * Handles a single client connection. For the intermediate milestone the logic
 * is limited to the join handshake.
 */
public final class ClientConnectionHandler implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientConnectionHandler.class);

    private final Socket socket;
    private final GameManager gameManager;

    public ClientConnectionHandler(Socket socket, GameManager gameManager) {
        this.socket = Objects.requireNonNull(socket, "socket must not be null");
        this.gameManager = Objects.requireNonNull(gameManager, "gameManager must not be null");
    }

    @Override
    public void run() {
        try (socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {

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
            String teamName = message.payload().get("teamName").getAsString().trim();
            String username = message.payload().get("username").getAsString().trim();

            if (gameCode.isEmpty() || teamName.isEmpty() || username.isEmpty()) {
                sendJoinRejected(writer, "Join request fields must not be blank");
                return;
            }

            var gameOpt = gameManager.findGame(gameCode);
            if (gameOpt.isEmpty()) {
                sendJoinRejected(writer, "Unknown game code: " + gameCode);
                return;
            }

            GameState gameState = gameOpt.get();
            GameState.RegistrationResult result = gameState.registerPlayer(teamName, username);
            if (!result.accepted()) {
                sendJoinRejected(writer, result.message());
                return;
            }

            sendJoinAccepted(writer, gameState);
            LOGGER.info("Player {} joined game {} (team {})", username, gameCode, teamName);
        } catch (IOException ex) {
            LOGGER.error("Error handling client connection", ex);
        }
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
