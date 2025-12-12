package pt.iskahoot.server.game;

import pt.iskahoot.common.model.Player;
import pt.iskahoot.common.model.Question;
import pt.iskahoot.common.model.QuestionType;
import pt.iskahoot.common.model.Team;
import pt.iskahoot.server.coordination.ModifiedCountDownLatch;
import pt.iskahoot.server.coordination.Barrier;

import java.io.BufferedWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guarda o estado mutável de uma sessão de jogo incluindo coordenação de rondas.
 */
public final class GameState {

    private final String code;
    private final GameConfiguration configuration;
    private final List<Question> questions;
    private final Instant createdAt;

    private final Map<String, Team> teamsByName;
    private final Map<String, Player> playersByUsername;
    
    // Estado do jogo e coordenação de rondas
    private GameStatus status;
    private int currentQuestionIndex;
    private final Map<String, PlayerConnection> playerConnections;
    private final Map<String, PlayerAnswer> currentRoundAnswers;
    
    // Coordenação para perguntas individuais e de equipa
    private ModifiedCountDownLatch currentCountDownLatch;
    private Map<String, Barrier> currentTeamBarriers;

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
        
        // Inicialização do estado do jogo
        this.status = GameStatus.WAITING;
        this.currentQuestionIndex = -1;
        this.playerConnections = new ConcurrentHashMap<>();
        this.currentRoundAnswers = new ConcurrentHashMap<>();
        this.currentCountDownLatch = null;
        this.currentTeamBarriers = new HashMap<>();
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
        // Bloqueio  garante que dois jogadores não entram ao mesmo tempo e nao repetiam nome 
      
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

    public synchronized void registerPlayerConnection(String username, BufferedWriter writer) {
        playerConnections.put(username, new PlayerConnection(username, writer));
    }

    public synchronized Map<String, PlayerConnection> getPlayerConnections() {
        return new HashMap<>(playerConnections);
    }

    public synchronized GameStatus getStatus() {
        return status;
    }

    public synchronized void setStatus(GameStatus status) {
        this.status = status;
    }

    public synchronized int getCurrentQuestionIndex() {
        return currentQuestionIndex;
    }

    public synchronized Question getCurrentQuestion() {
        if (currentQuestionIndex < 0 || currentQuestionIndex >= questions.size()) {
            return null;
        }
        return questions.get(currentQuestionIndex);
    }

    public synchronized void nextQuestion() {
        currentQuestionIndex++;
        currentRoundAnswers.clear();
    }

    public synchronized boolean hasMoreQuestions() {
        return currentQuestionIndex < questions.size() - 1;
    }

    public synchronized void recordAnswer(String username, int answerIndex, long responseTimeMs, int bonusFactor) {
        currentRoundAnswers.put(username, new PlayerAnswer(username, answerIndex, responseTimeMs, bonusFactor));
    }

    public synchronized Map<String, PlayerAnswer> getCurrentRoundAnswers() {
        return new HashMap<>(currentRoundAnswers);
    }

    public synchronized void setCurrentCountDownLatch(ModifiedCountDownLatch latch) {
        this.currentCountDownLatch = latch;
    }

    public synchronized ModifiedCountDownLatch getCurrentCountDownLatch() {
        return currentCountDownLatch;
    }

    public synchronized void setTeamBarrier(String teamName, Barrier barrier) {
        currentTeamBarriers.put(teamName, barrier);
    }

    public synchronized Barrier getTeamBarrier(String teamName) {
        return currentTeamBarriers.get(teamName);
    }

    public synchronized void clearTeamBarriers() {
        currentTeamBarriers.clear();
    }

    public synchronized Team getTeam(String teamName) {
        return teamsByName.get(teamName);
    }

    public synchronized Map<String, Team> getAllTeams() {
        return new HashMap<>(teamsByName);
    }

    /**
     * Calcula e atribui pontuação para a ronda atual.
     */
    public synchronized void calculateAndApplyScores() {
        Question currentQuestion = getCurrentQuestion();
        if (currentQuestion == null) {
            return;
        }

        if (currentQuestion.type() == QuestionType.INDIVIDUAL) {
            calculateIndividualScores(currentQuestion);
        } else {
            calculateTeamScores(currentQuestion);
        }
    }

    private void calculateIndividualScores(Question question) {
        int correctIndex = question.correctIndex();
        int basePoints = question.points();

        for (PlayerAnswer answer : currentRoundAnswers.values()) {
            if (answer.answerIndex() == correctIndex) {
                Player player = playersByUsername.get(answer.username());
                if (player != null) {
                    Team team = teamsByName.get(player.teamName());
                    if (team != null) {
                        // Aplicar pontuação com o bonusFactor obtido do CountDownLatch
                        int pointsToAdd = basePoints * answer.bonusFactor();
                        team.addScore(pointsToAdd);
                    }
                }
            }
        }
    }

    private void calculateTeamScores(Question question) {
        int correctIndex = question.correctIndex();
        int basePoints = question.points();

        // Para perguntas de equipa, calculamos por equipa
        Map<String, List<PlayerAnswer>> answersByTeam = new HashMap<>();
        for (PlayerAnswer answer : currentRoundAnswers.values()) {
            Player player = playersByUsername.get(answer.username());
            if (player != null) {
                answersByTeam.computeIfAbsent(player.teamName(), k -> new ArrayList<>()).add(answer);
            }
        }

        // Para cada equipa, verificamos se todos responderam
        for (Map.Entry<String, List<PlayerAnswer>> entry : answersByTeam.entrySet()) {
            String teamName = entry.getKey();
            List<PlayerAnswer> teamAnswers = entry.getValue();
            
            Team team = teamsByName.get(teamName);
            if (team == null) {
                continue;
            }

            // Verificar quantos membros responderam
            int expectedMembers = team.size();
            int respondedMembers = teamAnswers.size();
            
            // Contar respostas corretas
            long correctCount = teamAnswers.stream()
                .filter(a -> a.answerIndex() == correctIndex)
                .count();
            
            // Lógica de pontuação para equipas:
            // - Se todos responderam e todos acertaram: pontuação duplicada
            // - Se todos responderam mas nem todos acertaram: pontuação da melhor resposta
            // - Se tempo expirou sem todos responderem: sem bonificação
            
            if (correctCount == 0) {
                // Ninguém acertou - sem pontos
                continue;
            }
            
            if (respondedMembers == expectedMembers && correctCount == expectedMembers) {
                // Todos responderam e todos acertaram - pontuação duplicada
                team.addScore(basePoints * 2);
            } else if (correctCount > 0) {
                // Pelo menos alguém acertou - pontuação simples
                // Sem bonificação se não todos responderam ou não todos acertaram
                team.addScore(basePoints);
            }
        }
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

    public record PlayerConnection(String username, BufferedWriter writer) {
    }

    public record PlayerAnswer(String username, int answerIndex, long responseTimeMs, int bonusFactor) {
    }

    public enum GameStatus {
        WAITING,      // Aguardando jogadores
        IN_PROGRESS,  // Jogo em andamento
        FINISHED      // Jogo terminado
    }
}
