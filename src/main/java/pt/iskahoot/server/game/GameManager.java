package pt.iskahoot.server.game;

import pt.iskahoot.common.model.Player;
import pt.iskahoot.common.model.Question;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orquestra a criação e gestão dos jogos ativos no servidor.
 */
public final class GameManager {

    private final Map<String, GameState> gamesByCode;
    private final List<GameState> gamesInOrder;
    private final AtomicInteger gameCounter;

    public GameManager() {
        this.gamesByCode = new ConcurrentHashMap<>();
        this.gamesInOrder = Collections.synchronizedList(new ArrayList<>());
        this.gameCounter = new AtomicInteger(0);
    }

    public GameState createGame(GameConfiguration configuration, List<Question> pool) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        Objects.requireNonNull(pool, "pool must not be null");
        if (pool.size() < configuration.questionsPerGame()) {
            throw new IllegalArgumentException("Not enough questions to create the game");
        }

        // Código sequencial: game0, game1, game2, etc.
        int gameId = gameCounter.getAndIncrement();
        String code = "game" + gameId;
        
        List<Question> selected = selectQuestions(pool, configuration.questionsPerGame());
        GameState game = new GameState(code, configuration, selected);
        gamesByCode.put(code, game);
        gamesInOrder.add(game);
        return game;
    }

    public Optional<GameState> findGame(String code) {
        return Optional.ofNullable(gamesByCode.get(code));
    }

    public List<GameDescriptor> listGames() {
        List<GameDescriptor> descriptors = new ArrayList<>();
        synchronized (gamesInOrder) {
            for (GameState state : gamesInOrder) {
                descriptors.add(toDescriptor(state));
            }
        }
        return descriptors;
    }

    /**
     * Retorna a lista de GameStates em ordem de criação.
     */
    public List<GameState> getGamesInOrder() {
        synchronized (gamesInOrder) {
            return new ArrayList<>(gamesInOrder);
        }
    }

    private GameDescriptor toDescriptor(GameState state) {
        return new GameDescriptor(
            state.code(),
            state.configuration(),
            state.createdAt(),
            state.registeredPlayers());
    }

    private List<Question> selectQuestions(List<Question> pool, int count) {
        List<Question> copy = new ArrayList<>(pool);
        Collections.shuffle(copy);
        return copy.subList(0, count);
    }

    public record GameDescriptor(String code,
                                 GameConfiguration configuration,
                                 Instant createdAt,
                                 int registeredPlayers) {
    }
}
