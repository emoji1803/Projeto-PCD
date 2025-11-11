package pt.iskahoot.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import pt.iskahoot.common.net.Message;
import pt.iskahoot.common.net.MessageTypes;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * GUI Swing mínima para ligar ao servidor, enviar JOIN e mostrar o resultado.
 * Objetivo: cobrir a "GUI" solicitada na Fase 1 com foco no handshake.
 */
public final class IsKahootSwingClient {

    private IsKahootSwingClient() {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(IsKahootSwingClient::createAndShow);
    }

    private static void createAndShow() {
        JFrame frame = new JFrame("IsKahoot - Cliente");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setSize(560, 420);
        frame.setLocationRelativeTo(null);

        JTextField hostField = new JTextField("localhost");
        JTextField portField = new JTextField("8080");
        JTextField codeField = new JTextField();
        JTextField teamField = new JTextField();
        JTextField userField = new JTextField();
        JButton connectBtn = new JButton("Ligar e Entrar");
        JTextArea output = new JTextArea();
        output.setEditable(false);
        output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4,4,4,4);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0; c.gridy = 0; form.add(new JLabel("Servidor:"), c);
        c.gridx = 1; c.weightx = 1; form.add(hostField, c);
        c.gridx = 0; c.gridy = 1; c.weightx = 0; form.add(new JLabel("Porta:"), c);
        c.gridx = 1; c.weightx = 1; form.add(portField, c);
        c.gridx = 0; c.gridy = 2; c.weightx = 0; form.add(new JLabel("Código do jogo:"), c);
        c.gridx = 1; c.weightx = 1; form.add(codeField, c);
        c.gridx = 0; c.gridy = 3; c.weightx = 0; form.add(new JLabel("Equipa:"), c);
        c.gridx = 1; c.weightx = 1; form.add(teamField, c);
        c.gridx = 0; c.gridy = 4; c.weightx = 0; form.add(new JLabel("Utilizador:"), c);
        c.gridx = 1; c.weightx = 1; form.add(userField, c);
        c.gridx = 1; c.gridy = 5; c.weightx = 0; form.add(connectBtn, c);

        frame.setLayout(new BorderLayout());
        frame.add(form, BorderLayout.NORTH);
        frame.add(new JScrollPane(output), BorderLayout.CENTER);

        connectBtn.addActionListener(e -> {
            connectBtn.setEnabled(false);
            output.setText("");
            String host = hostField.getText().trim();
            String portTxt = portField.getText().trim();
            String code = codeField.getText().trim();
            String team = teamField.getText().trim();
            String user = userField.getText().trim();

            new Thread(() -> {
                try {
                    int port = Integer.parseInt(portTxt);
                    runJoin(host, port, code, team, user, output);
                } catch (Exception ex) {
                    append(output, "Erro: " + ex.getMessage());
                } finally {
                    SwingUtilities.invokeLater(() -> connectBtn.setEnabled(true));
                }
            }, "iskahoot-swing-join").start();
        });

        frame.setVisible(true);
    }

    private static void runJoin(String host, int port, String code, String team, String user, JTextArea output) throws Exception {
        append(output, String.format("A ligar a %s:%d...", host, port));
        try (Socket socket = new Socket(host, port);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {

            // SERVER_INFO
            String infoLine = reader.readLine();
            if (infoLine == null) throw new IllegalStateException("Ligação terminou sem SERVER_INFO");
            Message info = Message.fromJson(infoLine);
            if (!MessageTypes.SERVER_INFO.equals(info.type())) {
                append(output, "Mensagem inesperada: " + info.type());
            } else {
                append(output, info.payload().get("message").getAsString());
                JsonArray games = info.payload().getAsJsonArray("games");
                if (games != null) {
                    append(output, "Jogos disponíveis: " + games.size());
                }
            }

            // JOIN_REQUEST
            JsonObject payload = new JsonObject();
            payload.addProperty("gameCode", code);
            payload.addProperty("teamName", team);
            payload.addProperty("username", user);
            Message join = new Message(MessageTypes.JOIN_REQUEST, payload);
            writer.write(join.toJson());
            writer.newLine();
            writer.flush();

            // Resposta
            String line = reader.readLine();
            if (line == null) throw new IllegalStateException("Sem resposta ao JOIN");
            Message response = Message.fromJson(line);
            if (MessageTypes.JOIN_ACCEPTED.equals(response.type())) {
                append(output, "Ligação aceite!");
                append(output, String.format("Jogo %s tem %d perguntas.",
                        response.payload().get("gameCode").getAsString(),
                        response.payload().get("questions").getAsInt()));
            } else if (MessageTypes.JOIN_REJECTED.equals(response.type())) {
                String reason = response.payload().has("reason") ? response.payload().get("reason").getAsString() : "Sem motivo";
                append(output, "Ligação rejeitada: " + reason);
            } else {
                append(output, "Resposta inesperada: " + response.type());
            }
        }
    }

    private static void append(JTextArea area, String text) {
        SwingUtilities.invokeLater(() -> {
            area.append(text);
            area.append("\n");
        });
    }
}
