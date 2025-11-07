package pt.iskahoot.common.model;

import java.util.Objects;

/**
 * Basic player metadata. Scores and per-round information live in the server
 * side game state.
 */
public final class Player {

    private final String username;
    private final String teamName;

    public Player(String username, String teamName) {
        this.username = normalize(username, "username");
        this.teamName = normalize(teamName, "teamName");
    }

    private static String normalize(String value, String attribute) {
        Objects.requireNonNull(value, attribute + " must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(attribute + " must not be blank");
        }
        return trimmed;
    }

    public String username() {
        return username;
    }

    public String teamName() {
        return teamName;
    }

    @Override
    public String toString() {
        return "Player{" +
            "username='" + username + '\'' +
            ", teamName='" + teamName + '\'' +
            '}';
    }
}
