package Enzo.lima.service;

import Enzo.lima.model.EstadoDoJogo;
import Enzo.lima.view.MensagemView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.locks.ReentrantLock;

/**
 * SERVICE - Coordena a partida: vagas de jogador, controle de turno, desconexão e reinício.
 *
 * Fica entre o controller (que só conhece o WebSocket) e o model (que só conhece as regras).
 * Não depende do transporte: cada conexão é identificada por um id (String) e as mensagens saem
 * por uma {@link Saida} fornecida pelo controller.
 *
 * EXCLUSÃO MÚTUA
 *  O Spring chama o controller em threads diferentes (uma por conexão). Sem controle, duas jogadas
 *  simultâneas poderiam alterar tabuleiro, cartas e "vez" ao mesmo tempo (condição de corrida).
 *  Todo acesso ao estado e à tabela de jogadores acontece dentro da região crítica protegida por
 *  {@link #mutex} (lock/unlock em try/finally):
 *
 *    LOCK -> verifica a vez -> valida -> altera tabuleiro/cartas/vez -> envia atualizações -> UNLOCK
 *
 *  Enviar dentro do lock também garante que as mensagens de duas jogadas nunca se misturem.
 *  O Spring cria UMA instância deste bean (singleton), logo existe um único estado e um único mutex.
 */
@Service
public class PartidaService {

    /** Canal de saída usado para enviar uma mensagem a uma conexão (implementado pelo controller). */
    @FunctionalInterface
    public interface Saida {
        void enviar(String conexao, String mensagem);
    }

    private static final Logger log = LoggerFactory.getLogger(PartidaService.class);

    private final MensagemView view;

    /** Mutex (exclusão mútua) que protege a região crítica abaixo. */
    private final ReentrantLock mutex = new ReentrantLock();

    // ---------------- REGIÃO CRÍTICA (só acessar com o mutex travado) ----------------
    /** Memória compartilhada: o único estado da partida (alocado uma vez, aqui). */
    private final EstadoDoJogo estado = new EstadoDoJogo();
    /** Id da conexão de cada jogador; índices 1 e 2 (0 não usado). */
    private final String[] jogadores = new String[3];
    // ---------------------------------------------------------------------------------

    public PartidaService(MensagemView view) {
        this.view = view;
    }

    // ================================================================ conexão

    /**
     * Ocupa uma vaga para a conexão.
     * @return número do jogador (1 ou 2) ou 0 se a sala estiver cheia (o controller fecha a conexão).
     */
    public int conectar(String conexao, Saida saida) {
        mutex.lock();
        try {
            int n = jogadores[1] == null ? 1 : (jogadores[2] == null ? 2 : 0);
            if (n == 0) {
                saida.enviar(conexao, view.erro("SALA_CHEIA", "A partida já tem dois jogadores."));
                return 0;
            }
            jogadores[n] = conexao;
            log.info("Jogador {} conectado.", n);
            saida.enviar(conexao, view.conectado(n));

            if (jogadores[1] != null && jogadores[2] != null) {
                iniciarPartida(saida);
            } else {
                saida.enviar(conexao, view.aguardando("Aguardando o adversário..."));
            }
            return n;
        } finally {
            mutex.unlock();
        }
    }

    public void desconectar(String conexao, Saida saida) {
        mutex.lock();
        try {
            int n = jogadorDe(conexao);
            if (n == 0) return; // conexão rejeitada (sala cheia)
            jogadores[n] = null; // libera a vaga
            log.info("Jogador {} desconectado.", n);

            int outro = (n == 1) ? 2 : 1;
            if (jogadores[outro] != null) {
                saida.enviar(jogadores[outro], view.adversarioSaiu(n));
                if (estado.getFase() == EstadoDoJogo.Fase.JOGANDO) {
                    // abandono no meio da partida: o adversário vence (W.O.)
                    estado.encerrar(outro, "DESCONEXAO");
                    saida.enviar(jogadores[outro], view.vitoria(outro, "DESCONEXAO"));
                }
                estado.reiniciar(); // reaproveita a memória e volta a esperar um novo adversário
                saida.enviar(jogadores[outro], view.aguardando("Aguardando novo adversário..."));
                saida.enviar(jogadores[outro], view.estado(estado));
            } else {
                estado.reiniciar(); // ninguém restou: libera o estado da partida
            }
        } finally {
            mutex.unlock();
        }
    }

    // ================================================================ comandos

    /** MOVE|jogador|carta|lo|co|ld|cd -> valida, atualiza o estado e notifica os dois jogadores. */
    public void mover(String conexao, int jogadorMsg, String carta, int lo, int co, int ld, int cd, Saida saida) {
        mutex.lock(); // ---- INÍCIO DA REGIÃO CRÍTICA ----
        try {
            int n = jogadorDe(conexao);
            if (n == 0) return;
            if (jogadorMsg != n) { // o cliente não pode jogar "em nome" do outro
                saida.enviar(conexao, view.erro("JOGADOR_INVALIDO", "Você é o jogador " + n + "."));
                return;
            }
            EstadoDoJogo.Resultado r = estado.jogar(n, carta, lo, co, ld, cd);

            if (!r.ok()) { // jogada inválida: só quem errou é avisado e a vez NÃO muda
                saida.enviar(conexao, view.erro(r.codigoErro(), r.mensagemErro()));
                return;
            }

            broadcast(saida, view.movido(n, carta, lo, co, ld, cd));
            if (r.captura()) broadcast(saida, view.captura(n, r));
            broadcast(saida, view.troca(n, r));

            if (r.fim()) {
                int perdedor = (n == 1) ? 2 : 1;
                saida.enviar(jogadores[n], view.vitoria(n, r.motivo()));
                saida.enviar(jogadores[perdedor], view.derrota(perdedor, r.motivo()));
            } else {
                broadcast(saida, view.vez(estado.getVez()));
            }
            broadcast(saida, view.estado(estado)); // sincronização completa do tabuleiro
        } finally {
            mutex.unlock(); // ---- FIM DA REGIÃO CRÍTICA ----
        }
    }

    /** RESTART|jogador -> nova partida (somente após o fim). */
    public void reiniciar(String conexao, Saida saida) {
        mutex.lock();
        try {
            if (jogadorDe(conexao) == 0) return;
            if (estado.getFase() != EstadoDoJogo.Fase.FIM) {
                saida.enviar(conexao, view.erro("PARTIDA_INATIVA", "A partida ainda não terminou."));
                return;
            }
            iniciarPartida(saida);
        } finally {
            mutex.unlock();
        }
    }

    /** Envia uma mensagem de erro a uma conexão, respeitando a exclusão mútua. */
    public void erro(String conexao, String codigo, String mensagem, Saida saida) {
        mutex.lock();
        try {
            if (jogadorDe(conexao) == 0) return;
            saida.enviar(conexao, view.erro(codigo, mensagem));
        } finally {
            mutex.unlock();
        }
    }

    // ================================================================ auxiliares (chamar com o mutex travado)

    private void iniciarPartida(Saida saida) {
        estado.iniciar();
        log.info("Partida iniciada.");
        broadcast(saida, view.inicio(estado.getVez()));
        for (int n = 1; n <= 2; n++) {
            saida.enviar(jogadores[n], view.cartas(estado, n));
        }
        broadcast(saida, view.vez(estado.getVez()));
        broadcast(saida, view.estado(estado));
    }

    private int jogadorDe(String conexao) {
        if (conexao == null) return 0;
        if (conexao.equals(jogadores[1])) return 1;
        if (conexao.equals(jogadores[2])) return 2;
        return 0;
    }

    private void broadcast(Saida saida, String msg) {
        for (int n = 1; n <= 2; n++) {
            if (jogadores[n] != null) saida.enviar(jogadores[n], msg);
        }
    }
}

