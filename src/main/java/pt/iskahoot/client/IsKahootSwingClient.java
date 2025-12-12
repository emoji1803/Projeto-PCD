package pt.iskahoot.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import pt.iskahoot.common.net.Message;
import pt.iskahoot.common.net.MessageTypes;

import javax.swing.*;
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
 * Cliente IsKahoot com interface gráfica Swing.
 */
public class IsKahootSwingClient extends JFrame {

    private JTextArea logArea;
    private JPanel questionPanel;
    private JLabel questionLabel;
    private JLabel infoLabel;
    private ButtonGroup optionsGroup;
    private List<JRadioButton> optionButtons;
    private JButton submitButton;
    
    private BufferedReader reader;
    private BufferedWriter writer;
    private Socket socket;
    private String username;
    private long questionStartTime;

    public IsKahootSwingClient(String host, int port, String gameCode, String teamName, String username) {
        this.username = username;
        
        setTitle("IsKahoot - " + username + " (Team: " + teamName + ")");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        
        // Painel superior com info
        infoLabel = new JLabel("A conectar ao servidor...", SwingConstants.CENTER);
        infoLabel.setFont(new Font("Arial", Font.BOLD, 16));
        infoLabel.setOpaque(true);
        infoLabel.setBackground(Color.LIGHT_GRAY);
        infoLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        add(infoLabel, BorderLayout.NORTH);
        
        // Painel central para perguntas
        questionPanel = new JPanel();
        questionPanel.setLayout(new BoxLayout(questionPanel, BoxLayout.Y_AXIS));
        questionPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        JScrollPane scrollPane = new JScrollPane(questionPanel);
        add(scrollPane, BorderLayout.CENTER);
        
        // Área de log na parte inferior
        logArea = new JTextArea(8, 50);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane logScroll = new JScrollPane(logArea);
        add(logScroll, BorderLayout.SOUTH);
        
        setVisible(true);
        
        // Conectar ao servidor
        connectToServer(host, port, gameCode, teamName, username);
    }

    private void connectToServer(String host, int port, String gameCode, String teamName, String username) {
        new Thread(() -> {
            try {
                log("A conectar a " + host + ":" + port + "...");
                socket = new Socket(host, port);
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                
                // Handshake
                readServerInfo();
                sendJoinRequest(gameCode, teamName, username);
                
                if (awaitJoinResponse()) {
                    log("✓ Conectado com sucesso! Aguardando início do jogo...");
                    updateInfo("Aguardando início do jogo...");
                    gameLoop();
                }
            } catch (Exception e) {
                log("Erro: " + e.getMessage());
                JOptionPane.showMessageDialog(this, "Erro ao conectar: " + e.getMessage(), 
                    "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }).start();
    }

    private void readServerInfo() throws IOException {
        String line = reader.readLine();
        if (line == null) {
            throw new IOException("Ligação terminada");
        }
        Message info = Message.fromJson(line);
        if (MessageTypes.SERVER_INFO.equals(info.type())) {
            log("Servidor: " + info.payload().get("message").getAsString());
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
        log("Pedido de adesão enviado...");
    }

    private boolean awaitJoinResponse() throws IOException {
        String response = reader.readLine();
        if (response == null) {
            throw new IOException("Sem resposta do servidor");
        }
        
        Message message = Message.fromJson(response);
        if (MessageTypes.JOIN_ACCEPTED.equals(message.type())) {
            log("✓ Ligação aceite!");
            return true;
        } else if (MessageTypes.JOIN_REJECTED.equals(message.type())) {
            String reason = message.payload().has("reason") 
                ? message.payload().get("reason").getAsString() 
                : "Sem motivo";
            log("✗ Ligação rejeitada: " + reason);
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
                        default -> log("Mensagem não reconhecida: " + message.type());
                    }
                } catch (Exception e) {
                    log("Erro ao processar mensagem: " + e.getMessage());
                }
            });
        }
    }

    private void handleGameStart(Message message) {
        log("\n🎮 O JOGO COMEÇOU! 🎮");
        updateInfo("Jogo iniciado!");
    }

    private void handleQuestion(Message message) {
        JsonObject payload = message.payload();
        
        int questionNumber = payload.get("questionNumber").getAsInt();
        int totalQuestions = payload.get("totalQuestions").getAsInt();
        String prompt = payload.get("prompt").getAsString();
        int points = payload.get("points").getAsInt();
        String type = payload.get("type").getAsString();
        int timeLimit = payload.get("timeLimit").getAsInt();
        JsonArray options = payload.getAsJsonArray("options");
        
        questionStartTime = System.currentTimeMillis();
        
        // Atualizar info
        updateInfo(String.format("Pergunta %d/%d [%s] - %d pontos - %ds", 
            questionNumber, totalQuestions, type, points, timeLimit));
        
        // Limpar painel de perguntas
        questionPanel.removeAll();
        
        // Pergunta
        questionLabel = new JLabel("<html><h2>" + prompt + "</h2></html>");
        questionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        questionPanel.add(questionLabel);
        questionPanel.add(Box.createVerticalStrut(20));
        
        // Opções
        optionsGroup = new ButtonGroup();
        optionButtons = new ArrayList<>();
        
        for (int i = 0; i < options.size(); i++) {
            JRadioButton radioButton = new JRadioButton(options.get(i).getAsString());
            radioButton.setFont(new Font("Arial", Font.PLAIN, 14));
            radioButton.setAlignmentX(Component.CENTER_ALIGNMENT);
            radioButton.setActionCommand(String.valueOf(i));
            optionsGroup.add(radioButton);
            optionButtons.add(radioButton);
            questionPanel.add(radioButton);
            questionPanel.add(Box.createVerticalStrut(10));
        }
        
        // Botão de submeter
        questionPanel.add(Box.createVerticalStrut(20));
        submitButton = new JButton("Enviar Resposta");
        submitButton.setFont(new Font("Arial", Font.BOLD, 16));
        submitButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        submitButton.addActionListener(e -> submitAnswer());
        questionPanel.add(submitButton);
        
        questionPanel.revalidate();
        questionPanel.repaint();
        
        log(String.format("\n📝 Pergunta %d/%d: %s", questionNumber, totalQuestions, prompt));
    }

    private void submitAnswer() {
        // Verificar se alguma opção foi selecionada
        int selectedIndex = -1;
        for (int i = 0; i < optionButtons.size(); i++) {
            if (optionButtons.get(i).isSelected()) {
                selectedIndex = i;
                break;
            }
        }
        
        if (selectedIndex == -1) {
            JOptionPane.showMessageDialog(this, "Por favor, seleciona uma resposta!", 
                "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        long responseTime = System.currentTimeMillis() - questionStartTime;
        
        // Enviar resposta
        try {
            sendAnswer(selectedIndex, responseTime);
            submitButton.setEnabled(false);
            log("✓ Resposta enviada: Opção " + selectedIndex);
            updateInfo("Aguardando outros jogadores...");
        } catch (IOException e) {
            log("Erro ao enviar resposta: " + e.getMessage());
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
        
        log(String.format("\n📊 FIM DA RONDA %d", questionNumber));
        log(String.format("✓ Resposta correta: %d) %s", correctAnswer, correctOption));
        
        // Mostrar placar
        log("\nPLACAR ATUAL:");
        JsonArray leaderboard = payload.getAsJsonArray("leaderboard");
        for (int i = 0; i < leaderboard.size(); i++) {
            JsonObject team = leaderboard.get(i).getAsJsonObject();
            log(String.format("  %d. %s - %d pontos (%d jogadores)",
                i + 1,
                team.get("name").getAsString(),
                team.get("score").getAsInt(),
                team.get("players").getAsInt()));
        }
    }

    private void handleGameEnd(Message message) {
        JsonObject payload = message.payload();
        JsonArray finalLeaderboard = payload.getAsJsonArray("finalLeaderboard");
        
        log("\n🏆 FIM DE JOGO! 🏆");
        log("\nCLASSIFICAÇÃO FINAL:");
        
        // Ordenar por pontuação
        List<JsonObject> teams = new ArrayList<>();
        for (int i = 0; i < finalLeaderboard.size(); i++) {
            teams.add(finalLeaderboard.get(i).getAsJsonObject());
        }
        teams.sort((a, b) -> b.get("score").getAsInt() - a.get("score").getAsInt());
        
        for (int i = 0; i < teams.size(); i++) {
            JsonObject team = teams.get(i);
            String medal = i == 0 ? "🥇" : i == 1 ? "🥈" : i == 2 ? "🥉" : "  ";
            log(String.format("%s %d. %s - %d pontos (%d jogadores)",
                medal,
                i + 1,
                team.get("name").getAsString(),
                team.get("score").getAsInt(),
                team.get("players").getAsInt()));
        }
        
        updateInfo("Jogo terminado!");
        
        // Mostrar mensagem final
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(this, "Jogo terminado! Obrigado por jogar.", 
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
        // Configurar Look and Feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Ignore
        }
        
        SwingUtilities.invokeLater(() -> {
            // Se não houver argumentos, mostrar diálogo
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
                    new IsKahootSwingClient(
                        hostField.getText(),
                        Integer.parseInt(portField.getText()),
                        gameCodeField.getText(),
                        teamField.getText(),
                        usernameField.getText()
                    );
                }
            } else {
                new IsKahootSwingClient(
                    args[0],
                    Integer.parseInt(args[1]),
                    args[2],
                    args[3],
                    args[4]
                );
            }
        });
    }
}

