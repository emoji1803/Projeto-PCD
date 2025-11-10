package pt.iskahoot.common.net;

/**
 * Enumera os tipos de mensagem atualmente suportados no protocolo de handshake.
 */
public final class MessageTypes {

    private MessageTypes() {
    }

    public static final String JOIN_REQUEST = "JOIN_REQUEST";
    public static final String JOIN_ACCEPTED = "JOIN_ACCEPTED";
    public static final String JOIN_REJECTED = "JOIN_REJECTED";
    public static final String SERVER_INFO = "SERVER_INFO";
}
