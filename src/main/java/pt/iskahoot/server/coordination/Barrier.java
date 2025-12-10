package pt.iskahoot.server.coordination;

/**
 * Barreira de coordenação para perguntas de equipa.
 * Implementação própria usando variáveis condicionais (wait/notify).
 * 
 * Para perguntas de equipa: todos os membros da equipa devem responder
 * ou o tempo limite expirar. A pontuação da equipa é determinada pela
 * melhor resposta ou por consenso.
 */
public class Barrier {

    private final int expectedParticipants;
    private int arrivedCount;
    private boolean timeExpired;
    private final Object lock;

    /**
     * Cria uma barreira para um número esperado de participantes.
     * 
     * @param expectedParticipants número de participantes que devem chegar à barreira
     */
    public Barrier(int expectedParticipants) {
        if (expectedParticipants <= 0) {
            throw new IllegalArgumentException("expectedParticipants must be positive");
        }
        this.expectedParticipants = expectedParticipants;
        this.arrivedCount = 0;
        this.timeExpired = false;
        this.lock = new Object();
    }

    /**
     * Marca a chegada de um participante à barreira.
     * Se todos os participantes chegaram, notifica todas as threads em espera.
     * 
     * @return true se este foi o último participante a chegar, false caso contrário
     */
    public boolean barrierAction() {
        synchronized (lock) {
            arrivedCount++;
            boolean isLast = arrivedCount >= expectedParticipants;
            
            if (isLast) {
                // Último participante - notifica todos
                lock.notifyAll();
            }
            
            return isLast;
        }
    }

    /**
     * Aguarda até que todos os participantes cheguem à barreira ou o tempo expire.
     * 
     * @param timeoutMillis tempo máximo de espera em milissegundos
     * @return true se todos chegaram, false se o tempo expirou
     * @throws InterruptedException se a thread for interrompida
     */
    public boolean await(long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        synchronized (lock) {
            while (arrivedCount < expectedParticipants && !timeExpired) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    timeExpired = true;
                    lock.notifyAll(); // Notifica todos que o tempo expirou
                    return false;
                }
                lock.wait(remaining);
            }
            return arrivedCount >= expectedParticipants;
        }
    }

    /**
     * Marca a barreira como expirada (tempo limite atingido).
     * Notifica todas as threads em espera.
     */
    public void expire() {
        synchronized (lock) {
            timeExpired = true;
            lock.notifyAll();
        }
    }

    /**
     * Retorna o número de participantes que já chegaram.
     */
    public synchronized int getArrivedCount() {
        return arrivedCount;
    }

    /**
     * Verifica se a barreira está completa (todos chegaram ou tempo expirou).
     */
    public synchronized boolean isComplete() {
        return arrivedCount >= expectedParticipants || timeExpired;
    }

    /**
     * Verifica se o tempo expirou.
     */
    public synchronized boolean isTimeExpired() {
        return timeExpired;
    }

    /**
     * Retorna o número de participantes esperados.
     */
    public synchronized int getExpectedParticipants() {
        return expectedParticipants;
    }
}

