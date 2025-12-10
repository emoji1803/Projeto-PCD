package pt.iskahoot.server.game;

import java.util.Objects;

/**
 * Configuração do jogo que criamos no projeto.
 */
public final class GameConfiguration {

    private final int teamCount;
    private final int playersPerTeam;
    private final int questionsPerGame;

    public GameConfiguration(int teamCount, int playersPerTeam, int questionsPerGame) {
        this.teamCount = validate(teamCount, "teamCount");
        this.playersPerTeam = validate(playersPerTeam, "playersPerTeam");
        this.questionsPerGame = validate(questionsPerGame, "questionsPerGame");
    }

    private int validate(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be a positive integer");
        }
        return value;
    }

    public int teamCount() {
        return teamCount;
    }

    public int playersPerTeam() {
        return playersPerTeam;
    }

    public int questionsPerGame() {
        return questionsPerGame;
    }

    @Override
    public String toString() {
        return "GameConfiguration{" +
            "teamCount=" + teamCount +
            ", playersPerTeam=" + playersPerTeam +
            ", questionsPerGame=" + questionsPerGame +
            '}';
    }
}
