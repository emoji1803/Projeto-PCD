package pt.iskahoot.common.net;

/**
 * Enumera os tipos de mensagem suportados no protocolo.
 */
public final class MessageTypes {

    private MessageTypes() {
    }

    // Handshake inicial
    public static final String JOIN_REQUEST = "JOIN_REQUEST";
    public static final String JOIN_ACCEPTED = "JOIN_ACCEPTED";
    public static final String JOIN_REJECTED = "JOIN_REJECTED";
    public static final String SERVER_INFO = "SERVER_INFO";
    
    // Ciclo de jogo
    public static final String GAME_START = "GAME_START";
    public static final String QUESTION = "QUESTION";
    public static final String ANSWER = "ANSWER";
    public static final String ROUND_END = "ROUND_END";
    public static final String GAME_END = "GAME_END";
    public static final String WAITING_FOR_PLAYERS = "WAITING_FOR_PLAYERS";
}
