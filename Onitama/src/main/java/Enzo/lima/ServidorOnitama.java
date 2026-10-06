package Enzo.lima;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Servidor do Onitama (arquitetura CLIENTE/SERVIDOR sobre sockets).
 *
 * <pre>
 *  CLIENTE 1 (navegador) <-- socket --> SERVIDOR <-- socket --> CLIENTE 2 (navegador)
 * </pre>
 *
 * O servidor é a única autoridade: valida cada jogada ({@link EstadoDoJogo#jogar}), mantém o único
 * estado da partida e envia mensagens (notação em {@link Protocolo}) para os dois clientes. Os clientes
 * apenas desenham o que o servidor manda, por isso nunca ficam dessincronizados.
 *
 * EXCLUSÃO MÚTUA
 *  A biblioteca WebSocket chama onOpen/onClose/onMessage em threads diferentes (uma por conexão).
 *  Sem controle, duas jogadas simultâneas poderiam alterar tabuleiro, cartas e "vez" ao mesmo tempo
 *  (condição de corrida). Todo acesso ao estado e aos sockets dos jogadores acontece dentro da
 *  região crítica protegida por {@link #mutex} (lock/unlock em try/finally):
 *
 *    LOCK -> verifica a vez -> valida -> altera tabuleiro/cartas/vez -> envia atualizações -> UNLOCK
 *
 *  Enviar dentro do lock também garante que as mensagens de duas jogadas nunca se misturem.
 */
public class ServidorOnitama extends WebSocketServer {

    /** Mutex (exclusão mútua) que protege a região crítica abaixo. */
    private final ReentrantLock mutex = new ReentrantLock();

    // ---------------- REGIÃO CRÍTICA (só acessar com o mutex travado) ----------------
    /** Memória compartilhada: o único estado da partida (alocado uma vez, aqui). */
    private final EstadoDoJogo estado = new EstadoDoJogo();
    /** Conexão de cada jogador; índices 1 e 2 (0 não usado). */
    private final WebSocket[] jogadores = new WebSocket[3];
    // ---------------------------------------------------------------------------------

    public ServidorOnitama(int porta) {
        super(new InetSocketAddress(porta));
    }

    // ================================================================ conexão

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        mutex.lock();
        try {
            int n = jogadores[1] == null ? 1 : (jogadores[2] == null ? 2 : 0);
            if (n == 0) {
                enviar(conn, Protocolo.msg(Protocolo.ERROR, "SALA_CHEIA", "A partida já tem dois jogadores."));
                conn.close();
                return;
            }
            jogadores[n] = conn;
            log("Jogador " + n + " conectado: " + conn.getRemoteSocketAddress());
            enviar(conn, Protocolo.msg(Protocolo.CONNECT, n));

            if (jogadores[1] != null && jogadores[2] != null) {
                iniciarPartida();
            } else {
                enviar(conn, Protocolo.msg(Protocolo.WAIT, "Aguardando o adversário..."));
            }
        } finally {
            mutex.unlock();
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        mutex.lock();
        try {
            int n = jogadorDe(conn);
            if (n == 0) return; // conexão rejeitada (sala cheia)
            jogadores[n] = null; // libera a vaga
            log("Jogador " + n + " desconectado.");

            int outro = (n == 1) ? 2 : 1;
            if (jogadores[outro] != null) {
                enviar(jogadores[outro], Protocolo.msg(Protocolo.OPPONENT_LEFT, n));
                if (estado.getFase() == EstadoDoJogo.Fase.JOGANDO) {
                    // abandono no meio da partida: o adversário vence (W.O.)
                    estado.encerrar(outro, "DESCONEXAO");
                    enviar(jogadores[outro], Protocolo.msg(Protocolo.WIN, outro, "DESCONEXAO"));
                }
                estado.reiniciar(); // reaproveita a memória e volta a esperar um novo adversário
                enviar(jogadores[outro], Protocolo.msg(Protocolo.WAIT, "Aguardando novo adversário..."));
                enviar(jogadores[outro], estado.serializar());
            } else {
                estado.reiniciar(); // ninguém restou: libera o estado da partida
            }
        } finally {
            mutex.unlock();
        }
    }

    // ================================================================ mensagens

    @Override
    public void onMessage(WebSocket conn, String mensagem) {
        mutex.lock(); // ---- INÍCIO DA REGIÃO CRÍTICA ----
        try {
            log("Recebido: " + mensagem);
            int n = jogadorDe(conn);
            if (n == 0) return;

            String[] c = mensagem.trim().split("\\|", -1);
            try {
                switch (c[0]) {
                    case Protocolo.MOVE -> tratarMove(conn, n, c);
                    case Protocolo.RESTART -> tratarRestart(conn, n);
                    default -> enviar(conn, Protocolo.msg(Protocolo.ERROR, "COMANDO_DESCONHECIDO", "Comando inválido: " + c[0]));
                }
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                enviar(conn, Protocolo.msg(Protocolo.ERROR, "FORMATO_INVALIDO",
                        "Formato esperado: MOVE|jogador|carta|linhaOrigem|colOrigem|linhaDestino|colDestino"));
            }
        } finally {
            mutex.unlock(); // ---- FIM DA REGIÃO CRÍTICA ----
        }
    }

    /** MOVE|jogador|carta|lo|co|ld|cd -> valida, atualiza o estado e notifica os dois jogadores. */
    private void tratarMove(WebSocket conn, int n, String[] c) {
        if (c.length != 7) throw new ArrayIndexOutOfBoundsException();
        int jogadorMsg = Integer.parseInt(c[1]);
        if (jogadorMsg != n) { // o cliente não pode jogar "em nome" do outro
            enviar(conn, Protocolo.msg(Protocolo.ERROR, "JOGADOR_INVALIDO", "Você é o jogador " + n + "."));
            return;
        }
        EstadoDoJogo.Resultado r = estado.jogar(n, c[2],
                Integer.parseInt(c[3]), Integer.parseInt(c[4]), Integer.parseInt(c[5]), Integer.parseInt(c[6]));

        if (!r.ok()) { // jogada inválida: só quem errou é avisado e a vez NÃO muda
            enviar(conn, Protocolo.msg(Protocolo.ERROR, r.codigoErro(), r.mensagemErro()));
            return;
        }

        broadcast2(Protocolo.msg(Protocolo.MOVED, n, c[2], c[3], c[4], c[5], c[6]));
        if (r.captura()) {
            broadcast2(Protocolo.msg(Protocolo.CAPTURE, n, r.capLinha(), r.capColuna(),
                    Character.isUpperCase(r.capPeca()) ? "M" : "D"));
        }
        broadcast2(Protocolo.msg(Protocolo.SWAP, n, r.usada().id(), r.recebida().id()));

        if (r.fim()) {
            int perdedor = (n == 1) ? 2 : 1;
            enviar(jogadores[n], Protocolo.msg(Protocolo.WIN, n, r.motivo()));
            enviar(jogadores[perdedor], Protocolo.msg(Protocolo.LOSE, perdedor, r.motivo()));
        } else {
            broadcast2(Protocolo.msg(Protocolo.TURN, estado.getVez()));
        }
        broadcast2(estado.serializar()); // sincronização completa do tabuleiro
    }

    private void tratarRestart(WebSocket conn, int n) {
        if (estado.getFase() != EstadoDoJogo.Fase.FIM) {
            enviar(conn, Protocolo.msg(Protocolo.ERROR, "PARTIDA_INATIVA", "A partida ainda não terminou."));
            return;
        }
        iniciarPartida();
    }

    // ================================================================ auxiliares (chamar com o mutex travado)

    private void iniciarPartida() {
        estado.iniciar();
        log("Partida iniciada.");
        broadcast2(Protocolo.msg(Protocolo.START, estado.getVez()));
        // cartas recebidas por cada jogador (a carta de reserva é pública)
        String[] partes = estado.serializar().split("\\|");
        String reserva = partes[5];
        for (int n = 1; n <= 2; n++) {
            enviar(jogadores[n], Protocolo.msg(Protocolo.CARDS, n, partes[n + 2], reserva));
        }
        broadcast2(Protocolo.msg(Protocolo.TURN, estado.getVez()));
        broadcast2(estado.serializar());
    }

    private int jogadorDe(WebSocket conn) {
        if (jogadores[1] == conn) return 1;
        if (jogadores[2] == conn) return 2;
        return 0;
    }

    private void broadcast2(String msg) {
        for (int n = 1; n <= 2; n++) enviar(jogadores[n], msg);
    }

    private void enviar(WebSocket conn, String msg) {
        if (conn == null) return;
        try {
            conn.send(msg);
            log("Enviado   : " + msg);
        } catch (Exception e) { // socket caiu no meio do envio; onClose cuidará da limpeza
            log("Falha ao enviar (" + e.getClass().getSimpleName() + ")");
        }
    }

    private static void log(String texto) {
        System.out.println("[Servidor] " + texto);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("[Servidor] Erro de comunicação: " + ex);
    }

    @Override
    public void onStart() {
        log("Servidor Onitama ligado! Aguardando jogadores na porta " + getPort());
    }

    public static void main(String[] args) {
        int porta = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        new ServidorOnitama(porta).start();
    }
}