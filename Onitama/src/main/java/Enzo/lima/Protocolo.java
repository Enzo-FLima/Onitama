package Enzo.lima;

/**
 * Notação própria das mensagens trocadas entre servidor e clientes.
 * Formato de texto: CODIGO|campo1|campo2|...   (separador = '|').
 * A documentação completa de cada mensagem está no README.md.
 */
public final class Protocolo {
    private Protocolo() {}

    public static final String SEP = "|";

    // ---- Cliente -> Servidor ----
    /** MOVE|jogador|carta|linhaOrigem|colOrigem|linhaDestino|colDestino */
    public static final String MOVE = "MOVE";
    /** RESTART|jogador  (nova partida, só após o fim) */
    public static final String RESTART = "RESTART";

    // ---- Servidor -> Cliente ----
    /** CONNECT|jogador  - identifica o jogador dono da conexão */
    public static final String CONNECT = "CONNECT";
    /** WAIT|texto  - aguardando adversário */
    public static final String WAIT = "WAIT";
    /** START|primeiroJogador */
    public static final String START = "START";
    /** CARDS|jogador|carta1,carta2|reserva  - cartas recebidas no início */
    public static final String CARDS = "CARDS";
    /** TURN|jogador  - de quem é a vez */
    public static final String TURN = "TURN";
    /** MOVED|jogador|carta|linhaOrigem|colOrigem|linhaDestino|colDestino  - jogada validada */
    public static final String MOVED = "MOVED";
    /** CAPTURE|jogador|linha|coluna|peca  (peca: D = discípulo, M = mestre) */
    public static final String CAPTURE = "CAPTURE";
    /** SWAP|jogador|cartaUsada|cartaRecebida */
    public static final String SWAP = "SWAP";
    /** STATE|vez|fase|mao1|mao2|reserva|tabuleiro - sincronização completa */
    public static final String STATE = "STATE";
    /** WIN|jogador|motivo */
    public static final String WIN = "WIN";
    /** LOSE|jogador|motivo */
    public static final String LOSE = "LOSE";
    /** OPPONENT_LEFT|jogador  - adversário desconectou */
    public static final String OPPONENT_LEFT = "OPPONENT_LEFT";
    /** ERROR|codigo|mensagem */
    public static final String ERROR = "ERROR";

    public static String msg(Object... campos) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < campos.length; i++) {
            if (i > 0) sb.append(SEP);
            sb.append(campos[i]);
        }
        return sb.toString();
    }
}

