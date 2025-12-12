package pt.iskahoot.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import pt.iskahoot.common.net.Message;
import pt.iskahoot.common.net.MessageTypes;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Cliente IsKahoot com GUI melhorada - inspirada no vídeo do professor.
 * Layout: Placar à direita, perguntas à esquerda, logs em baixo.
 */
public class IsKahootImprovedSwingClient extends JFrame {

    private JLabel questionLabel;
    private JLabel infoLabel;
    private ButtonGroup optionsGroup;
    private List<JRadioButton> optionButtons;
    private JButton submitButton;
    private JPanel questionPanel;
    
    // Placar lateral
    private DefaultListModel<String> leaderboardModel;
    private JList<String> leaderboardList;
    
    // Log área
    private JTextArea logArea;
    
    private BufferedReader reader;
    private BufferedWriter writer;
    private Socket socket;
    private String username;
    private String teamName;
    private long questionStartTime;

    public IsKahootImprovedSwingClient(String host, int port, String gameCode, String teamName, String username) {
        this.username = username;
        this.teamName = teamName;
        
        setTitle("IsKahoot - " + username + " [" + teamName + "]");
        setSize(1200, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        
        // Painel superior - Info
        initInfoPanel();
        
        // Painel central - Split: Perguntas (esquerda) e Placar (direita)
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(800);
        splitPane.setLeftComponent(createQuestionPanel());
        splitPane.setRightComponent(createLeaderboardPanel());
        add(splitPane, BorderLayout.CENTER);
        
        // Painel inferior - Logs
        add(createLogPanel(), BorderLayout.SOUTH);
        
        setVisible(true);
        
        // Conectar ao servidor
        connectToServer(host, port, gameCode, teamName, username);
    }

    private void initInfoPanel() {
        infoLabel = new JLabel("A conectar ao servidor...", SwingConstants.CENTER);
        infoLabel.setFont(new Font("Arial", Font.BOLD, 18));
        infoLabel.setOpaque(true);
        infoLabel.setBackground(new Color(70, 130, 180));
        infoLabel.setForeground(Color.WHITE);
        infoLabel.setBorder(new EmptyBorder(15, 10, 15, 10));
        add(infoLabel, BorderLayout.NORTH);
    }

    private JPanel createQuestionPanel() {
        JPanel container = new JPanel(new BorderLayout());
        
        questionPanel = new JPanel();
        questionPanel.setLayout(new BoxLayout(questionPanel, BoxLayout.Y_AXIS));
        questionPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        questionPanel.setBackground(Color.WHITE);
        
        JScrollPane scrollPane = new JScrollPane(questionPanel);
        container.add(scrollPane, BorderLayout.CENTER);
        
        return container;
    }

    private JPanel createLeaderboardPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(Color.GRAY, 2),
            "PLACAR",
            TitledBorder.CENTER,
            TitledBorder.TOP,
            new Font("Arial", Font.BOLD, 16)));
        
        leaderboardModel = new DefaultListModel<>();
        leaderboardList = new JList<>(leaderboardModel);
        leaderboardList.setFont(new Font("Monospaced", Font.PLAIN, 14));
        leaderboardList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        JScrollPane scrollPane = new JScrollPane(leaderboardList);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }

    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Histórico"));
        
        logArea = new JTextArea(6, 50);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        
        JScrollPane scrollPane = new JScrollPane(logArea);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }

    private void connectToServer(String host, int port, String gameCode, String teamName, String username) {
        new Thread(() -> {
            try {
                log("Conectando a " + host + ":" + port + "...");
                socket = new Socket(host, port);
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                
                readServerInfo();
                sendJoinRequest(gameCode, teamName, username);
                
                if (awaitJoinResponse()) {
                    log("✓ Conectado! Aguardando início...");
                    updateInfo("Aguardando início do jogo...");
                    gameLoop();
                }
            } catch (Exception e) {
                log("Erro: " + e.getMessage());
                JOptionPane.showMessageDialog(this, "Erro: " + e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }).start();
    }

    private void readServerInfo() throws IOException {
        String line = reader.readLine();
        if (line != null) {
            Message info = Message.fromJson(line);
            if (MessageTypes.SERVER_INFO.equals(info.type())) {
                log("Servidor: " + info.payload().get("message").getAsString());
            }
        }
    }

    private void sendJoinRequest(String gameCode, String teamName, String username) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("gameCode", gameCode);
        payload.addProperty("teamName", teamName);
        payload.addProperty("username", username);
        Message join = new Message(MessageTypes.JOIN_REQUEST, payload);
        writer.write(join.toJson());
        writer.newLine();
        writer.flush();
    }

    private boolean awaitJoinResponse() throws IOException {
        String response = reader.readLine();
        if (response == null) return false;
        
        Message message = Message.fromJson(response);
        if (MessageTypes.JOIN_ACCEPTED.equals(message.type())) {
            log("✓ Ligação aceite!");
            return true;
        } else if (MessageTypes.JOIN_REJECTED.equals(message.type())) {
            String reason = message.payload().has("reason") 
                ? message.payload().get("reason").getAsString() : "Sem motivo";
            log("✗ Rejeitado: " + reason);
            return false;
        }
        return false;
    }

    private void gameLoop() throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            Message message = Message.fromJson(line);
            
            SwingUtilities.invokeLater(() -> {
                try {
                    switch (message.type()) {
                        case MessageTypes.GAME_START -> handleGameStart(message);
                        case MessageTypes.QUESTION -> handleQuestion(message);
                        case MessageTypes.ROUND_END -> handleRoundEnd(message);
                        case MessageTypes.GAME_END -> handleGameEnd(message);
                        default -> log("Mensagem: " + message.type());
                    }
                } catch (Exception e) {
                    log("Erro: " + e.getMessage());
                }
            });
        }
    }

    private void handleGameStart(Message message) {
        log("\n🎮 JOGO COMEÇOU!");
        updateInfo("Jogo em andamento!");
        infoLabel.setBackground(new Color(34, 139, 34));
    }

    private void handleQuestion(Message message) {
        JsonObject payload = message.payload();
        
        int questionNumber = payload.get("questionNumber").getAsInt();
        int totalQuestions = payload.get("totalQuestions").getAsInt();
        String prompt = payload.get("prompt").getAsString();
        int points = payload.get("points").getAsInt();
        String type = payload.get("type").getAsString();
        JsonArray options = payload.getAsJsonArray("options");
        
        questionStartTime = System.currentTimeMillis();
        
        updateInfo(String.format("Pergunta %d/%d [%s] - %d pontos", 
            questionNumber, totalQuestions, type, points));
        
        questionPanel.removeAll();
        
        // Título da pergunta
        JLabel title = new JLabel(String.format("<html><div style='width:700px;'><h2 style='color:#2E86AB;'>Pergunta %d/%d</h2></div></html>", 
            questionNumber, totalQuestions));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        questionPanel.add(title);
        questionPanel.add(Box.createVerticalStrut(10));
        
        // Texto da pergunta
        questionLabel = new JLabel(String.format("<html><div style='width:700px;'><h3>%s</h3></div></html>", prompt));
        questionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        questionPanel.add(questionLabel);
        questionPanel.add(Box.createVerticalStrut(20));
        
        // Opções
        optionsGroup = new ButtonGroup();
        optionButtons = new ArrayList<>();
        
        for (int i = 0; i < options.size(); i++) {
            JRadioButton radioButton = new JRadioButton(options.get(i).getAsString());
            radioButton.setFont(new Font("Arial", Font.PLAIN, 16));
            radioButton.setAlignmentX(Component.LEFT_ALIGNMENT);
            radioButton.setActionCommand(String.valueOf(i));
            radioButton.setOpaque(true);
            radioButton.setBackground(new Color(240, 240, 240));
            radioButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1),
                new EmptyBorder(10, 10, 10, 10)));
            
            optionsGroup.add(radioButton);
            optionButtons.add(radioButton);
            questionPanel.add(radioButton);
            questionPanel.add(Box.createVerticalStrut(10));
        }
        
        // Botão submit
        questionPanel.add(Box.createVerticalStrut(20));
        submitButton = new JButton("✓ ENVIAR RESPOSTA");
        submitButton.setFont(new Font("Arial", Font.BOLD, 18));
        submitButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        submitButton.setBackground(new Color(76, 175, 80));
        submitButton.setForeground(Color.WHITE);
        submitButton.setFocusPainted(false);
        submitButton.addActionListener(e -> submitAnswer());
        questionPanel.add(submitButton);
        
        questionPanel.revalidate();
        questionPanel.repaint();
        
        log(String.format("Pergunta %d: %s", questionNumber, prompt));
    }

    private void submitAnswer() {
        int selectedIndex = -1;
        for (int i = 0; i < optionButtons.size(); i++) {
            if (optionButtons.get(i).isSelected()) {
                selectedIndex = i;
                break;
            }
        }
        
        if (selectedIndex == -1) {
            JOptionPane.showMessageDialog(this, "Seleciona uma resposta!", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        long responseTime = System.currentTimeMillis() - questionStartTime;
        
        try {
            sendAnswer(selectedIndex, responseTime);
            submitButton.setEnabled(false);
            submitButton.setText("✓ Resposta enviada!");
            log("✓ Enviado: Opção " + selectedIndex + " (" + responseTime + "ms)");
            updateInfo("Aguardando outros jogadores...");
        } catch (IOException e) {
            log("Erro ao enviar: " + e.getMessage());
        }
    }

    private void sendAnswer(int answerIndex, long responseTime) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("answerIndex", answerIndex);
        payload.addProperty("responseTime", responseTime);
        
        Message message = new Message(MessageTypes.ANSWER, payload);
        writer.write(message.toJson());
        writer.newLine();
        writer.flush();
    }

    private void handleRoundEnd(Message message) {
        JsonObject payload = message.payload();
        
        int questionNumber = payload.get("questionNumber").getAsInt();
        int correctAnswer = payload.get("correctAnswer").getAsInt();
        String correctOption = payload.get("correctOption").getAsString();
        
        // Mostrar resposta correta visualmente
        for (int i = 0; i < optionButtons.size(); i++) {
            JRadioButton btn = optionButtons.get(i);
            if (i == correctAnswer) {
                btn.setBackground(new Color(76, 175, 80)); // Verde
                btn.setForeground(Color.WHITE);
            } else {
                btn.setBackground(new Color(244, 67, 54)); // Vermelho
                btn.setForeground(Color.WHITE);
            }
        }
        
        log(String.format("📊 Ronda %d | ✓ Resposta correta: %d) %s", questionNumber, correctAnswer, correctOption));
        
        // Atualizar placar com destaque para pontos ganhos
        JsonArray leaderboard = payload.getAsJsonArray("leaderboard");
        updateLeaderboardWithRoundInfo(leaderboard);
        
        // Habilitar botão para próxima pergunta
        submitButton.setText("Aguardando próxima pergunta...");
    }

    private void updateLeaderboard(JsonArray leaderboard) {
        updateLeaderboardWithRoundInfo(leaderboard);
    }

    private void updateLeaderboardWithRoundInfo(JsonArray leaderboard) {
        leaderboardModel.clear();
        leaderboardModel.addElement("╔═════════════════════════════╗");
        leaderboardModel.addElement("║       PLACAR ATUAL          ║");
        leaderboardModel.addElement("╠═════════════════════════════╣");
        
        List<JsonObject> teams = new ArrayList<>();
        for (int i = 0; i < leaderboard.size(); i++) {
            teams.add(leaderboard.get(i).getAsJsonObject());
        }
        teams.sort((a, b) -> b.get("score").getAsInt() - a.get("score").getAsInt());
        
        for (int i = 0; i < teams.size(); i++) {
            JsonObject team = teams.get(i);
            String name = team.get("name").getAsString();
            int score = team.get("score").getAsInt();
            int players = team.get("players").getAsInt();
            
            String medal = i == 0 ? "🥇" : i == 1 ? "🥈" : i == 2 ? "🥉" : "  ";
            String highlight = name.equals(teamName) ? " ★" : "";
            
            leaderboardModel.addElement(String.format("║ %s %d. %-15s%s", medal, i+1, name, highlight));
            leaderboardModel.addElement(String.format("║    %d pts | %d jogadores", score, players));
            leaderboardModel.addElement("║─────────────────────────────");
        }
        leaderboardModel.addElement("╚═════════════════════════════╝");
    }

    private void handleGameEnd(Message message) {
        JsonObject payload = message.payload();
        
        log("\n🏆 FIM DE JOGO!");
        updateInfo("Jogo terminado!");
        infoLabel.setBackground(new Color(220, 20, 60));
        
        updateLeaderboard(payload.getAsJsonArray("finalLeaderboard"));
        
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(this, "Jogo terminado!\nObrigado por jogar!", 
                "Fim de Jogo", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    private void updateInfo(String text) {
        SwingUtilities.invokeLater(() -> infoLabel.setText(text));
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Ignore
        }
        
        SwingUtilities.invokeLater(() -> {
            if (args.length != 5) {
                JPanel panel = new JPanel(new GridLayout(5, 2, 5, 5));
                JTextField hostField = new JTextField("localhost");
                JTextField portField = new JTextField("8080");
                JTextField gameCodeField = new JTextField("game0");
                JTextField teamField = new JTextField();
                JTextField usernameField = new JTextField();
                
                panel.add(new JLabel("Servidor:"));
                panel.add(hostField);
                panel.add(new JLabel("Porta:"));
                panel.add(portField);
                panel.add(new JLabel("Código do jogo:"));
                panel.add(gameCodeField);
                panel.add(new JLabel("Equipa:"));
                panel.add(teamField);
                panel.add(new JLabel("Nome:"));
                panel.add(usernameField);
                
                int result = JOptionPane.showConfirmDialog(null, panel, 
                    "IsKahoot - Configuração", JOptionPane.OK_CANCEL_OPTION);
                
                if (result == JOptionPane.OK_OPTION) {
                    new IsKahootImprovedSwingClient(
                        hostField.getText(),
                        Integer.parseInt(portField.getText()),
                        gameCodeField.getText(),
                        teamField.getText(),
                        usernameField.getText()
                    );
                }
            } else {
                new IsKahootImprovedSwingClient(args[0], Integer.parseInt(args[1]), 
                    args[2], args[3], args[4]);
            }
        });
    }
}

