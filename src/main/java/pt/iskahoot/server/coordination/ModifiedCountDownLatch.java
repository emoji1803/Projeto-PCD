package pt.iskahoot.server.coordination;

/**
 * CountDownLatch modificado para suportar bónus para os primeiros jogadores.
 * Implementação própria sem usar java.util.concurrent.CountDownLatch.
 * 
 * Para perguntas individuais: cada jogador que responde decrementa o contador.
 * Os primeiros 'bonusCount' jogadores recebem bónus multiplicado por 'bonusFactor'.
 */
public class ModifiedCountDownLatch {

    private final int bonusFactor;
    private final int bonusCount;
    private final int waitPeriod;
    private int count;
    private int answeredCount;
    private final Object lock;

    /**
     * Cria um ModifiedCountDownLatch.
     * 
     * @param bonusFactor multiplicador de pontos para os primeiros jogadores
     * @param bonusCount quantos jogadores recebem o bónus
     * @param waitPeriod período de espera em segundos
     * @param count número inicial de respostas esperadas
     */
    public ModifiedCountDownLatch(int bonusFactor, int bonusCount, int waitPeriod, int count) {
        if (bonusFactor < 1) {
            throw new IllegalArgumentException("bonusFactor must be at least 1");
        }
        if (bonusCount < 0) {
            throw new IllegalArgumentException("bonusCount must be non-negative");
        }
        if (waitPeriod <= 0) {
            throw new IllegalArgumentException("waitPeriod must be positive");
        }
        if (count < 0) {
            throw new IllegalArgumentException("count must be non-negative");
        }
        
        this.bonusFactor = bonusFactor;
        this.bonusCount = bonusCount;
        this.waitPeriod = waitPeriod;
        this.count = count;
        this.answeredCount = 0;
        this.lock = new Object();
    }

    /**
     * Retorna o número atual de contagem.
     */
    public synchronized int countdown() {
        return count;
    }

    /**
     * Aguarda até que a contagem chegue a zero ou o tempo expire.
     * Bloqueia a thread que chama este método.
     * 
     * @throws InterruptedException se a thread for interrompida enquanto aguarda
     */
    public void await() throws InterruptedException {
        long deadline = System.currentTimeMillis() + (waitPeriod * 1000L);
        synchronized (lock) {
            while (count > 0) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    // Tempo expirou
                    return;
                }
                lock.wait(remaining);
            }
        }
    }

    /**
     * Decrementa a contagem e retorna o fator de pontuação para este jogador.
     * Se este jogador está entre os primeiros 'bonusCount', retorna bonusFactor.
     * Caso contrário, retorna 1.
     * 
     * @return fator de pontuação (bonusFactor para os primeiros, 1 para os restantes)
     */
    public int countDown() {
        synchronized (lock) {
            if (count > 0) {
                count--;
                answeredCount++;
                
                // Notifica threads em espera
                lock.notifyAll();
                
                // Retorna o fator de bónus se este jogador está entre os primeiros
                if (answeredCount <= bonusCount) {
                    return bonusFactor;
                } else {
                    return 1;
                }
            }
            return 1; // Se já estava em zero, não há bónus
        }
    }

    /**
     * Retorna o número de jogadores que já responderam.
     */
    public synchronized int getAnsweredCount() {
        return answeredCount;
    }

    /**
     * Verifica se todos os jogadores já responderam ou o tempo expirou.
     */
    public synchronized boolean isComplete() {
        return count == 0;
    }
}

