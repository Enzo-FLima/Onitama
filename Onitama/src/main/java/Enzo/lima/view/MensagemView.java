package Enzo.lima.view;

import Enzo.lima.model.Carta;
import Enzo.lima.model.EstadoDoJogo;
import org.springframework.stereotype.Component;

/**
 * VIEW - "Desenha" o model na notação do protocolo ({@link Protocolo}).
 *
 * O front-end (HTML/JS) é a interface gráfica de verdade; no backend a camada de visão é a
 * representação textual que o servidor envia pelo socket. Nenhuma regra de jogo fica aqui:
 * esta classe apenas lê o {@link EstadoDoJogo} e formata as mensagens.
 */
@Component
public class MensagemView {

    public String conectado(int jogador) {
        return Protocolo.msg(Protocolo.CONNECT, jogador);
    }

    public String aguardando(String texto) {
        return Protocolo.msg(Protocolo.WAIT, texto);
    }

    public String inicio(int primeiroJogador) {
        return Protocolo.msg(Protocolo.START, primeiroJogador);
    }

    /** CARDS|jogador|carta1,carta2|reserva  (a carta de reserva é pública). */
    public String cartas(EstadoDoJogo estado, int jogador) {
        return Protocolo.msg(Protocolo.CARDS, jogador, mao(estado, jogador), id(estado.getReserva()));
    }

    public String vez(int jogador) {
        return Protocolo.msg(Protocolo.TURN, jogador);
    }

    public String movido(int jogador, String carta, int lo, int co, int ld, int cd) {
        return Protocolo.msg(Protocolo.MOVED, jogador, carta, lo, co, ld, cd);
    }

    /** CAPTURE|jogador|linha|coluna|peca  (M = mestre, D = discípulo). */
    public String captura(int jogador, EstadoDoJogo.Resultado r) {
        return Protocolo.msg(Protocolo.CAPTURE, jogador, r.capLinha(), r.capColuna(),
                Character.isUpperCase(r.capPeca()) ? "M" : "D");
    }

    public String troca(int jogador, EstadoDoJogo.Resultado r) {
        return Protocolo.msg(Protocolo.SWAP, jogador, r.usada().id(), r.recebida().id());
    }

    public String vitoria(int jogador, String motivo) {
        return Protocolo.msg(Protocolo.WIN, jogador, motivo);
    }

    public String derrota(int jogador, String motivo) {
        return Protocolo.msg(Protocolo.LOSE, jogador, motivo);
    }

    public String adversarioSaiu(int jogador) {
        return Protocolo.msg(Protocolo.OPPONENT_LEFT, jogador);
    }

    public String erro(String codigo, String mensagem) {
        return Protocolo.msg(Protocolo.ERROR, codigo, mensagem);
    }

    /**
     * Foto do estado para sincronizar os clientes:
     * STATE|vez|fase|mao1a,mao1b|mao2a,mao2b|reserva|25 casas (linha a linha)
     */
    public String estado(EstadoDoJogo estado) {
        StringBuilder tab = new StringBuilder(EstadoDoJogo.TAM * EstadoDoJogo.TAM);
        for (int l = 0; l < EstadoDoJogo.TAM; l++) {
            for (int c = 0; c < EstadoDoJogo.TAM; c++) tab.append(estado.getPeca(l, c));
        }
        return Protocolo.msg(Protocolo.STATE, estado.getVez(), estado.getFase(),
                mao(estado, 1), mao(estado, 2), id(estado.getReserva()), tab);
    }

    private static String mao(EstadoDoJogo estado, int jogador) {
        return id(estado.getCarta(jogador, 0)) + "," + id(estado.getCarta(jogador, 1));
    }

    private static String id(Carta c) {
        return c == null ? "-" : c.id();
    }
}

