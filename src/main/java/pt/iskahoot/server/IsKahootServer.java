package pt.iskahoot.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.iskahoot.common.model.Quiz;
import pt.iskahoot.server.game.GameManager;
import pt.iskahoot.server.net.ServerRuntime;
import pt.iskahoot.server.repository.QuizRepository;
import pt.iskahoot.server.tui.ServerCli;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Ponto de entrada do processo de servidor IsKahoot.
 */
public final class IsKahootServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(IsKahootServer.class);

    private IsKahootServer() {
    }

    public static void main(String[] args) throws Exception {
        ServerOptions options = ServerOptions.parse(args);
        LOGGER.info("Starting server with options: {}", options);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        QuizRepository repository = new QuizRepository(gson);
        Quiz quiz = loadQuiz(repository, options.questionsPath());

        GameManager gameManager = new GameManager();
        try (ServerRuntime runtime = new ServerRuntime(options.port(), gameManager)) {
            runtime.start();
            new ServerCli(gameManager, quiz.questions()).run();
        }

        LOGGER.info("Server stopped.");
    }

    private static Quiz loadQuiz(QuizRepository repository, Path path) throws IOException {
        LOGGER.info("Loading quiz from {}", path.toAbsolutePath());
        Quiz quiz = repository.load(path);
        LOGGER.info("Loaded quiz '{}' with {} questions", quiz.name(), quiz.questions().size());
        return quiz;
    }
}
