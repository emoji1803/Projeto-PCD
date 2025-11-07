package pt.iskahoot.common.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a team within a game.
 */
public final class Team {

    private final String name;
    private final List<Player> players;
    private int score;

    public Team(String name) {
        this.name = normalize(name);
        this.players = new ArrayList<>();
        this.score = 0;
    }

    private static String normalize(String value) {
        Objects.requireNonNull(value, "name must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return trimmed;
    }

    public synchronized void addPlayer(Player player) {
        players.add(player);
    }

    public synchronized List<Player> players() {
        return Collections.unmodifiableList(new ArrayList<>(players));
    }

    public synchronized int size() {
        return players.size();
    }

    public synchronized void addScore(int delta) {
        score += delta;
    }

    public synchronized int score() {
        return score;
    }

    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return "Team{" +
            "name='" + name + '\'' +
            ", players=" + players +
            ", score=" + score +
            '}';
    }
}
