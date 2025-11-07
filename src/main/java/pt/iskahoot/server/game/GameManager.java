package pt.iskahoot.server.game;

import pt.iskahoot.common.model.Player;
import pt.iskahoot.common.model.Question;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates creation and lookup of running games.
 */
public final class GameManager {

    private final Map<String, GameState> gamesByCode;
    private final SecureRandom random;

    public GameManager() {
        this.gamesByCode = new ConcurrentHashMap<>();
        this.random = new SecureRandom();
    }

    public GameState createGame(GameConfiguration configuration, List<Question> pool) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        Objects.requireNonNull(pool, "pool must not be null");
        if (pool.size() < configuration.questionsPerGame()) {
            throw new IllegalArgumentException("Not enough questions to create the game");
        }

        String code = generateUniqueCode();
        List<Question> selected = selectQuestions(pool, configuration.questionsPerGame());
        GameState game = new GameState(code, configuration, selected);
        gamesByCode.put(code, game);
        return game;
    }

    public Optional<GameState> findGame(String code) {
        return Optional.ofNullable(gamesByCode.get(code));
    }

    public List<GameDescriptor> listGames() {
        List<GameDescriptor> descriptors = new ArrayList<>();
        for (GameState state : gamesByCode.values()) {
            descriptors.add(toDescriptor(state));
        }
        descriptors.sort((a, b) -> b.createdAt().compareTo(a.createdAt()));
        return descriptors;
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
        Collections.shuffle(copy, random);
        return copy.subList(0, count);
    }

    private String generateUniqueCode() {
        String code;
        do {
            code = generateCode();
        } while (gamesByCode.containsKey(code));
        return code;
    }

    private String generateCode() {
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            int idx = random.nextInt(alphabet.length());
            sb.append(alphabet.charAt(idx));
        }
        return sb.toString();
    }

    public record GameDescriptor(String code,
                                 GameConfiguration configuration,
                                 Instant createdAt,
                                 int registeredPlayers) {
    }
}
