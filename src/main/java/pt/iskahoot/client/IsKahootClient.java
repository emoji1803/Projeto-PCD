package pt.iskahoot.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import pt.iskahoot.common.net.Message;
import pt.iskahoot.common.net.MessageTypes;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.Scanner;

/**
 * Ponto de entrada do cliente IsKahoot. Nesta entrega intermédia limita-se
 * a negociar o handshake inicial com o servidor e a apresentar informação
 * básica sobre o jogo e as equipas registadas.
 */
public final class IsKahootClient {

    private IsKahootClient() {
    }

    public static void main(String[] args) throws Exception {
        ClientOptions options = ClientOptions.parse(args);
        System.out.printf("A ligar a %s:%d...%n", options.host(), options.port());
        try (Socket socket = new Socket(options.host(), options.port());
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {

            // Etapa 1: recolher informação geral do servidor, incluindo jogos ativos.
            readServerInfo(reader);
            // Etapa 2: enviar pedido de adesão com código de jogo, equipa e utilizador.
            sendJoinRequest(writer, options);
            // Etapa 3: aguardar resposta do servidor e apresentar feedback ao utilizador.
            awaitJoinResponse(reader);
        }
    }

    private static void readServerInfo(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null) {
            throw new IOException("Ligação terminada antes de receber informação do servidor");
        }
        // O servidor inicia sempre com uma mensagem SERVER_INFO que descreve o estado atual.
        Message info = Message.fromJson(line);
        if (!MessageTypes.SERVER_INFO.equals(info.type())) {
            System.out.println("Aviso: servidor enviou mensagem inesperada: " + info.type());
            return;
        }
        System.out.println(info.payload().get("message").getAsString());
        JsonArray games = info.payload().getAsJsonArray("games");
        if (games != null && !games.isEmpty()) {
            System.out.println("Jogos disponíveis:");
            games.forEach(g -> {
                JsonObject obj = g.getAsJsonObject();
                System.out.printf("- Código: %s | Equipas: %d | Jogadores/equipa: %d | Inscritos: %d%n",
                    obj.get("code").getAsString(),
                    obj.get("teams").getAsInt(),
                    obj.get("playersPerTeam").getAsInt(),
                    obj.get("registeredPlayers").getAsInt());
            });
        } else {
            System.out.println("Nenhum jogo disponível de momento. Solicite a um docente para criar um.");
        }
        System.out.println();
    }

    private static void sendJoinRequest(BufferedWriter writer, ClientOptions options) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("gameCode", options.gameCode());
        payload.addProperty("teamName", options.teamName());
        payload.addProperty("username", options.username());
        // O pedido segue o protocolo definido: cada mensagem é JSON com type/payload.
        Message join = new Message(MessageTypes.JOIN_REQUEST, payload);
        writer.write(join.toJson());
        writer.newLine();
        writer.flush();
    }

    private static void awaitJoinResponse(BufferedReader reader) throws IOException {
        String response = reader.readLine();
        if (response == null) {
            throw new IOException("Ligação terminou sem resposta do servidor");
        }

        Message message = Message.fromJson(response);
        // A resposta pode ser JOIN_ACCEPTED (com resumo do jogo) ou JOIN_REJECTED.
        switch (message.type()) {
            case MessageTypes.JOIN_ACCEPTED -> displayJoinAccepted(message);
            case MessageTypes.JOIN_REJECTED -> displayJoinRejected(message);
            default -> System.out.println("Resposta inesperada: " + message.type());
        }
    }

    private static void displayJoinAccepted(Message message) {
        System.out.println("Ligação aceite!");
        JsonArray teams = message.payload().getAsJsonArray("teams");
        System.out.printf("Jogo %s tem %d perguntas.%n",
            message.payload().get("gameCode").getAsString(),
            message.payload().get("questions").getAsInt());
        if (teams != null) {
            System.out.println("Estado atual das equipas:");
            teams.forEach(e -> {
                JsonObject obj = e.getAsJsonObject();
                System.out.printf("- %s | Jogadores: %d | Pontos: %d%n",
                    obj.get("name").getAsString(),
                    obj.get("players").getAsInt(),
                    obj.get("score").getAsInt());
            });
        }
    }

    private static void displayJoinRejected(Message message) {
        String reason = message.payload().has("reason")
            ? message.payload().get("reason").getAsString()
            : "Sem motivo indicado";
        System.out.println("Ligação rejeitada: " + reason);
    }

    /**
     * Aggregates user-provided options.
     */
    public record ClientOptions(String host, int port, String gameCode, String teamName, String username) {

        private static final int DEFAULT_PORT = 8080;

        public ClientOptions {
            Objects.requireNonNull(host, "host must not be null");
            Objects.requireNonNull(gameCode, "gameCode must not be null");
            Objects.requireNonNull(teamName, "teamName must not be null");
            Objects.requireNonNull(username, "username must not be null");
        }

        public static ClientOptions parse(String[] args) {
            if (args.length == 5) {
                return new ClientOptions(
                    args[0],
                    Integer.parseInt(args[1]),
                    args[2].toUpperCase(Locale.ROOT),
                    args[3],
                    args[4]);
            }

            if (args.length == 0) {
                return promptUser();
            }

            printUsageAndExit();
            return null; // unreachable
        }

        private static ClientOptions promptUser() {
            Scanner scanner = new Scanner(System.in);
            try {
                System.out.print("Servidor (IP ou host) [localhost]: ");
                String host = defaultIfBlank(scanner.nextLine(), "localhost");

                System.out.print("Porta [8080]: ");
                String rawPort = scanner.nextLine();
                int port = rawPort.isBlank() ? DEFAULT_PORT : Integer.parseInt(rawPort);

                System.out.print("Código do jogo: ");
                String gameCode = scanner.nextLine().trim().toUpperCase(Locale.ROOT);

                System.out.print("Nome da equipa: ");
                String team = scanner.nextLine().trim();

                System.out.print("Nome de utilizador: ");
                String username = scanner.nextLine().trim();

                if (gameCode.isEmpty() || team.isEmpty() || username.isEmpty()) {
                    throw new IllegalArgumentException("Os campos código, equipa e utilizador são obrigatórios");
                }

                return new ClientOptions(host, port, gameCode, team, username);
            } finally {
                scanner.close();
            }
        }

        private static String defaultIfBlank(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value.trim();
        }

        private static void printUsageAndExit() {
            System.out.println("""
                Uso: java IsKahootClient <host> <porta> <codigo_jogo> <equipa> <username>
                Sem argumentos será iniciado um modo interativo na consola.
                """);
            System.exit(0);
        }
    }
}
