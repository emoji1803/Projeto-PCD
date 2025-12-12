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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Scanner;

/**
 * Ponto de entrada do cliente IsKahoot. Negocia o handshake inicial com o servidor e apresenta informação
 * básica sobre o jogo e as equipas registadas. Usamos o GSon para parsear o JSON.
 */
public final class IsKahootClient {

    private IsKahootClient() {
    }

    public static void main(String[] args) throws Exception {
        ClientOptions options = ClientOptions.parse(args);
        System.out.printf("A ligar a %s:%d...%n", options.host(), options.port());
        try (Socket socket = new Socket(options.host(), options.port());
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
             Scanner scanner = new Scanner(System.in)) {

            // Etapa 1: recolher info geral do servidor, incluindo jogos ativos
            readServerInfo(reader);
            
            // Etapa 2: enviar pedido de adesão com código de jogo, equipa e utilizador
            sendJoinRequest(writer, options);
            
            // Etapa 3: aguardar resposta do servidor
            if (!awaitJoinResponse(reader)) {
                return; // Conexão rejeitada
            }

            // Etapa 4: Loop de jogo - processar mensagens do servidor
            gameLoop(reader, writer, scanner);
            
            System.out.println("\nObrigado por jogar IsKahoot!");
        }
    }

    private static void readServerInfo(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null) {
            throw new IOException("Ligação terminada antes de receber informação do servidor");
        }
        // O servidor manda sempre uma mensagem SERVER_INFO que descreve o estado atual.
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

    private static boolean awaitJoinResponse(BufferedReader reader) throws IOException {
        String response = reader.readLine();
        if (response == null) {
            throw new IOException("Ligação terminou sem resposta do servidor");
        }

        Message message = Message.fromJson(response);
        // A resposta pode ser JOIN_ACCEPTED (jogo continua) ou JOIN_REJECTED.
        switch (message.type()) {
            case MessageTypes.JOIN_ACCEPTED -> {
                displayJoinAccepted(message);
                return true;
            }
            case MessageTypes.JOIN_REJECTED -> {
                displayJoinRejected(message);
                return false;
            }
            default -> {
                System.out.println("Resposta inesperada: " + message.type());
                return false;
            }
        }
    }

    /**
     * Loop principal do jogo - processa mensagens do servidor.
     */
    private static void gameLoop(BufferedReader reader, BufferedWriter writer, Scanner scanner) throws IOException {
        String line;
        boolean gameActive = true;
        
        while (gameActive && (line = reader.readLine()) != null) {
            Message message = Message.fromJson(line);
            
            switch (message.type()) {
                case MessageTypes.GAME_START -> handleGameStart(message);
                case MessageTypes.QUESTION -> handleQuestion(message, writer, scanner);
                case MessageTypes.ROUND_END -> handleRoundEnd(message);
                case MessageTypes.GAME_END -> {
                    handleGameEnd(message);
                    gameActive = false;
                }
                case MessageTypes.WAITING_FOR_PLAYERS -> handleWaitingForPlayers(message);
                default -> System.out.println("Mensagem não reconhecida: " + message.type());
            }
        }
    }

    private static void handleGameStart(Message message) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("🎮 O JOGO COMEÇOU! 🎮");
        System.out.println("=".repeat(60));
        System.out.printf("Jogo: %s%n", message.payload().get("gameCode").getAsString());
        System.out.printf("Total de perguntas: %d%n", message.payload().get("totalQuestions").getAsInt());
        System.out.println("=".repeat(60) + "\n");
    }

    private static void handleQuestion(Message message, BufferedWriter writer, Scanner scanner) throws IOException {
        JsonObject payload = message.payload();
        
        int questionNumber = payload.get("questionNumber").getAsInt();
        int totalQuestions = payload.get("totalQuestions").getAsInt();
        String prompt = payload.get("prompt").getAsString();
        int points = payload.get("points").getAsInt();
        String type = payload.get("type").getAsString();
        int timeLimit = payload.get("timeLimit").getAsInt();
        JsonArray options = payload.getAsJsonArray("options");

        System.out.println("\n" + "-".repeat(60));
        System.out.printf("📝 PERGUNTA %d/%d [%s] (%d pontos)%n", questionNumber, totalQuestions, type, points);
        System.out.println("-".repeat(60));
        System.out.println(prompt);
        System.out.println();
        
        for (int i = 0; i < options.size(); i++) {
            System.out.printf("  %d) %s%n", i, options.get(i).getAsString());
        }
        
        System.out.println("-".repeat(60));
        System.out.printf("Tempo limite: %d segundos%n", timeLimit);
        System.out.printf("Sua resposta (0-%d): ", options.size() - 1);
        
        long startTime = System.currentTimeMillis();
        
        // Ler resposta do utilizador
        int answer = -1;
        try {
            if (scanner.hasNextLine()) {
                String input = scanner.nextLine().trim();
                answer = Integer.parseInt(input);
                
                if (answer < 0 || answer >= options.size()) {
                    System.out.println("⚠️  Resposta inválida! Será registada como incorreta.");
                    answer = -1;
                }
            }
        } catch (NumberFormatException e) {
            System.out.println("⚠️  Resposta inválida! Será registada como incorreta.");
            answer = -1;
        }
        
        long responseTime = System.currentTimeMillis() - startTime;
        
        // Enviar resposta ao servidor
        sendAnswer(writer, answer, responseTime);
        System.out.println("✓ Resposta enviada!");
    }

    private static void sendAnswer(BufferedWriter writer, int answerIndex, long responseTime) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("answerIndex", answerIndex);
        payload.addProperty("responseTime", responseTime);
        
        Message message = new Message(MessageTypes.ANSWER, payload);
        writer.write(message.toJson());
        writer.newLine();
        writer.flush();
    }

    private static void handleRoundEnd(Message message) {
        JsonObject payload = message.payload();
        
        int questionNumber = payload.get("questionNumber").getAsInt();
        int correctAnswer = payload.get("correctAnswer").getAsInt();
        String correctOption = payload.get("correctOption").getAsString();
        
        System.out.println("\n" + "=".repeat(60));
        System.out.println("📊 FIM DA RONDA " + questionNumber);
        System.out.println("=".repeat(60));
        System.out.printf("✓ Resposta correta: %d) %s%n", correctAnswer, correctOption);
        System.out.println();
        System.out.println("PLACAR ATUAL:");
        
        JsonArray leaderboard = payload.getAsJsonArray("leaderboard");
        for (int i = 0; i < leaderboard.size(); i++) {
            JsonObject team = leaderboard.get(i).getAsJsonObject();
            System.out.printf("  %d. %s - %d pontos (%d jogadores)%n",
                i + 1,
                team.get("name").getAsString(),
                team.get("score").getAsInt(),
                team.get("players").getAsInt());
        }
        System.out.println("=".repeat(60) + "\n");
    }

    private static void handleGameEnd(Message message) {
        JsonObject payload = message.payload();
        JsonArray finalLeaderboard = payload.getAsJsonArray("finalLeaderboard");
        
        System.out.println("\n" + "=".repeat(60));
        System.out.println("🏆 FIM DE JOGO! 🏆");
        System.out.println("=".repeat(60));
        System.out.println("CLASSIFICAÇÃO FINAL:");
        System.out.println();
        
        // Ordenar equipas por pontuação (descendente)
        List<JsonObject> teams = new ArrayList<>();
        for (int i = 0; i < finalLeaderboard.size(); i++) {
            teams.add(finalLeaderboard.get(i).getAsJsonObject());
        }
        teams.sort((a, b) -> b.get("score").getAsInt() - a.get("score").getAsInt());
        
        for (int i = 0; i < teams.size(); i++) {
            JsonObject team = teams.get(i);
            String medal = i == 0 ? "🥇" : i == 1 ? "🥈" : i == 2 ? "🥉" : "  ";
            System.out.printf("%s %d. %s - %d pontos (%d jogadores)%n",
                medal,
                i + 1,
                team.get("name").getAsString(),
                team.get("score").getAsInt(),
                team.get("players").getAsInt());
        }
        System.out.println("=".repeat(60));
    }

    private static void handleWaitingForPlayers(Message message) {
        System.out.println("⏳ Aguardando mais jogadores para iniciar o jogo...");
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
     * opcoes ao user
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
