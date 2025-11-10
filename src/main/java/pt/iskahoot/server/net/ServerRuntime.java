package pt.iskahoot.server.net;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.iskahoot.server.game.GameManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coordena a vertente de rede do servidor: aceita ligações dos clientes e
 * delega a gestão para handlers dedicados.
 */
public final class ServerRuntime implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerRuntime.class);

    private final int port;
    private final GameManager gameManager;
    private final AtomicBoolean running;
    private ServerSocket serverSocket;
    private ExecutorService clientExecutor;

    public ServerRuntime(int port, GameManager gameManager) {
        this.port = port;
        this.gameManager = Objects.requireNonNull(gameManager, "gameManager must not be null");
        this.running = new AtomicBoolean(false);
    }

    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Server is already running");
        }

        this.serverSocket = new ServerSocket(port);
        this.clientExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setName("iskahoot-client-" + t.getId());
            t.setDaemon(true);
            return t;
        });

        Thread acceptThread = new Thread(this::acceptLoop, "iskahoot-acceptor");
        acceptThread.setDaemon(true);
        acceptThread.start();
        LOGGER.info("Server started on port {}", port);
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket socket = serverSocket.accept();
                LOGGER.info("Accepted connection from {}", socket.getRemoteSocketAddress());
                // Cada ligação é tratada por uma thread do executor, permitindo múltiplos clientes.
                clientExecutor.submit(new ClientConnectionHandler(socket, gameManager));
            } catch (IOException ex) {
                if (running.get()) {
                    LOGGER.error("Error accepting connection", ex);
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        LOGGER.info("Stopping server...");
        if (serverSocket != null) {
            serverSocket.close();
        }
        if (clientExecutor != null) {
            clientExecutor.shutdownNow();
        }
    }
}
