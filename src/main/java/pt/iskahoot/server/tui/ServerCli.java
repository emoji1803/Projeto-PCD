package pt.iskahoot.server.tui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.iskahoot.common.model.Question;
import pt.iskahoot.server.game.GameConfiguration;
import pt.iskahoot.server.game.GameManager;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

/**
 * Simple text-based interface to manage server commands during the initial
 * milestone.
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

    private void stop() {
        running = false;
        System.out.println("A terminar CLI do servidor...");
    }
}
