package pt.iskahoot.server.tui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.iskahoot.common.model.Question;
import pt.iskahoot.server.game.GameConfiguration;
import pt.iskahoot.server.game.GameManager;
import pt.iskahoot.server.game.GameOrchestrator;
import pt.iskahoot.server.game.GameState;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Scanner;

/**
 * TUI simples , com run , deal de conecoes e comandos.
 */
public final class ServerCli implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerCli.class);

    private final Scanner scanner;
    private final GameManager gameManager;
    private final List<Question> questionPool;

    private volatile boolean running;

    public ServerCli(GameManager gameManager, List<Question> questionPool) {
        this.scanner = new Scanner(System.in);
        this.gameManager = gameManager;
        this.questionPool = questionPool;
        this.running = true;
    }

    @Override
    public void run() {
        printHeader();
        while (running) {
            System.out.print("iskahoot> ");
            if (!scanner.hasNextLine()) {
                break;
            }
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) {
                continue;
            }
            handleCommand(line);
        }
        scanner.close();
    }

    private void printHeader() {
        System.out.println("""
            Bem-vindo ao IsKahoot!
            Comandos disponíveis:
              new <equipas> <jogadores_por_equipa> <perguntas>
              start <codigo_jogo>
              list
              help
              exit
            """);
    }

    private void handleCommand(String rawCommand) {
        String[] parts = rawCommand.split("\\s+");
        String command = parts[0].toLowerCase(Locale.ROOT);
        try {
            switch (command) {
                case "new" -> createNewGame(parts);
                case "start" -> startGame(parts);
                case "list" -> listGames();
                case "help" -> printHeader();
                case "exit", "quit" -> stop();
                default -> System.out.println("Comando desconhecido. Escreva 'help' para ajuda.");
            }
        } catch (IllegalArgumentException ex) {
            System.out.println("Erro: " + ex.getMessage());
            LOGGER.warn("Invalid command: {}", rawCommand, ex);
        }
    }

    private void createNewGame(String[] parts) {
        if (parts.length != 4) {
            throw new IllegalArgumentException("Uso: new <equipas> <jogadores_por_equipa> <perguntas>");
        }
        try {
            int teams = Integer.parseInt(parts[1]);
            int playersPerTeam = Integer.parseInt(parts[2]);
            int questionsPerGame = Integer.parseInt(parts[3]);

            GameConfiguration config = new GameConfiguration(teams, playersPerTeam, questionsPerGame);
            var game = gameManager.createGame(config, questionPool);
            System.out.printf("Jogo criado com código %s%n", game.code());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Os parâmetros devem ser números inteiros positivos", ex);
        }
    }

    private void listGames() {
        var games = gameManager.listGames();
        if (games.isEmpty()) {
            System.out.println("Não existem jogos ativos.");
            return;
        }
        System.out.println("Jogos ativos:");
        for (GameManager.GameDescriptor descriptor : games) {
            System.out.printf("- Código: %s | Equipas: %d | Jogadores/equipa: %d | Participantes: %d%n",
                descriptor.code(),
                descriptor.configuration().teamCount(),
                descriptor.configuration().playersPerTeam(),
                descriptor.registeredPlayers());
        }
    }

    private void startGame(String[] parts) {
        if (parts.length != 2) {
            throw new IllegalArgumentException("Uso: start <codigo_jogo>");
        }
        
        String gameCode = parts[1].toUpperCase(Locale.ROOT);
        Optional<GameState> gameOpt = gameManager.findGame(gameCode);
        
        if (gameOpt.isEmpty()) {
            System.out.println("Jogo não encontrado: " + gameCode);
            return;
        }
        
        GameState gameState = gameOpt.get();
        
        if (gameState.getStatus() != GameState.GameStatus.WAITING) {
            System.out.println("Jogo " + gameCode + " já está em andamento ou terminado.");
            return;
        }
        
        System.out.printf("A iniciar jogo %s com %d jogadores...%n", 
            gameCode, gameState.registeredPlayers());
        
        // Iniciar o jogo numa thread separada
        Thread gameThread = new Thread(() -> {
            try {
                GameOrchestrator orchestrator = new GameOrchestrator(gameState);
                orchestrator.startGame();
            } catch (Exception e) {
                LOGGER.error("Error running game {}", gameCode, e);
                System.err.println("Erro ao executar jogo: " + e.getMessage());
            }
        }, "game-" + gameCode);
        
        gameThread.setDaemon(false);
        gameThread.start();
        
        System.out.println("Jogo iniciado!");
    }

    private void stop() {
        running = false;
        System.out.println("A terminar CLI do servidor...");
    }
}
