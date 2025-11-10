package pt.iskahoot.server;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Representa as opções de linha de comandos do processo de servidor.
 */
public record ServerOptions(int port, Path questionsPath) {

    private static final int DEFAULT_PORT = 8080;
    private static final Path DEFAULT_QUESTIONS = Paths.get("src/main/resources/questions.json");

    public ServerOptions {
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        Objects.requireNonNull(questionsPath, "questionsPath must not be null");
    }

    public static ServerOptions parse(String[] args) {
        int port = DEFAULT_PORT;
        Path questions = DEFAULT_QUESTIONS;

        for (String arg : args) {
            if (arg.startsWith("--port=")) {
                port = Integer.parseInt(arg.substring("--port=".length()));
            } else if (arg.startsWith("--questions=")) {
                questions = Paths.get(arg.substring("--questions=".length()));
            } else if ("--help".equals(arg) || "-h".equals(arg)) {
                printUsageAndExit();
            }
        }

        return new ServerOptions(port, questions);
    }

    private static void printUsageAndExit() {
        System.out.println("""
            Uso: java IsKahootServer [opções]
              --port=<número>           Porta TCP do servidor (default 8080)
              --questions=<ficheiro>    Caminho para o ficheiro JSON com perguntas
            """);
        System.exit(0);
    }
}
