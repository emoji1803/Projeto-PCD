package pt.iskahoot.server.game;

import pt.iskahoot.common.model.Player;
import pt.iskahoot.common.model.Question;
import pt.iskahoot.common.model.Team;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Keeps the mutable state of a game session. For the intermediate milestone the
 * class focuses on registration and setup logic; question coordination will be
 * added later.
 */
public final class GameState {

    private final String code;
    private final GameConfiguration configuration;
    private final List<Question> questions;
    private final Instant createdAt;

    private final Map<String, Team> teamsByName;
    private final Map<String, Player> playersByUsername;

    public GameState(String code, GameConfiguration configuration, List<Question> questions) {
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        Objects.requireNonNull(questions, "questions must not be null");
        if (questions.isEmpty()) {
            throw new IllegalArgumentException("questions must not be empty");
        }
        this.questions = List.copyOf(questions);
        this.createdAt = Instant.now();
        this.teamsByName = new HashMap<>();
        this.playersByUsername = new HashMap<>();
    }

    public String code() {
        return code;
    }

    public GameConfiguration configuration() {
        return configuration;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public List<Question> questions() {
        return Collections.unmodifiableList(questions);
    }

    public synchronized RegistrationResult registerPlayer(String teamName, String username) {
        if (playersByUsername.containsKey(username)) {
            return RegistrationResult.rejected("Username is already in use: " + username);
        }

        Team team = teamsByName.computeIfAbsent(teamName, Team::new);
        if (teamsByName.size() > configuration.teamCount()) {
            teamsByName.remove(teamName);
            return RegistrationResult.rejected("Maximum number of teams reached");
        }

        if (team.size() >= configuration.playersPerTeam()) {
            return RegistrationResult.rejected("Team " + teamName + " is already full");
        }

        Player player = new Player(username, teamName);
        team.addPlayer(player);
        playersByUsername.put(username, player);
        return RegistrationResult.accepted(player, team.size(), currentTeamCount());
    }

    public synchronized int currentTeamCount() {
        return teamsByName.size();
    }

    public synchronized int registeredPlayers() {
        return playersByUsername.size();
    }

    public synchronized List<TeamSnapshot> snapshotTeams() {
        List<TeamSnapshot> snapshot = new ArrayList<>();
        for (Team team : teamsByName.values()) {
            snapshot.add(new TeamSnapshot(team.name(), team.size(), team.score()));
        }
        return snapshot;
    }

    public record TeamSnapshot(String name, int players, int score) {
    }

    public record RegistrationResult(boolean accepted,
                                     String message,
                                     Player player,
                                     int teamSize,
                                     int totalTeams) {

        static RegistrationResult accepted(Player player, int teamSize, int totalTeams) {
            return new RegistrationResult(true, "Player registered", player, teamSize, totalTeams);
        }

        static RegistrationResult rejected(String message) {
            return new RegistrationResult(false, message, null, 0, 0);
        }
    }
}
