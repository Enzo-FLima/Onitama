package Enzo.lima;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ESTADO CENTRAL DA PARTIDA (a "memória" do jogo) + REGRAS DO ONITAMA.
 *
 * <pre>
 * EstadoDoJogo
 *  ├── tabuleiro[5][5]   (char: '.' vazio, 'a'/'A' jogador 1, 'b'/'B' jogador 2; maiúscula = Mestre)
 *  ├── maos[1] e maos[2] (2 cartas de cada jogador)
 *  ├── reserva           (carta central/lateral)
 *  ├── vez               (1 ou 2)
 *  └── estado            (ESPERANDO, JOGANDO, FIM)
 * </pre>
 *
 * GERENCIAMENTO DE MEMÓRIA
 *  - ALOCADA   : existe UMA única instância desta classe por servidor. O tabuleiro (char[5][5]) e as
 *                mãos (Carta[3][2]) são alocados uma vez, no construtor.
 *  - UTILIZADA : toda validação lê diretamente esses mesmos arrays; nenhuma cópia do estado é feita.
 *  - ATUALIZADA: {@link #jogar} altera o tabuleiro/mãos/vez EM LUGAR (in-place). As cartas são
 *                apenas referências para o catálogo imutável {@link Carta#TODAS}.
 *  - COMPARTILHADA: o estado é compartilhado pelas threads do servidor (uma por conexão). A classe NÃO
 *                é thread-safe por si só: o {@link ServidorOnitama} protege TODO acesso com um
 *                ReentrantLock (exclusão mútua).
 *  - LIBERADA  : {@link #reiniciar()} reaproveita os mesmos arrays (sem realocar) e zera as referências
 *                antigas; ao encerrar o servidor o coletor de lixo da JVM libera o objeto inteiro.
 *                Para o adversário nunca é guardada cópia do estado: só mensagens são enviadas.
 */
public class EstadoDoJogo {

    public enum Fase { ESPERANDO, JOGANDO, FIM }

    /** Resultado de uma tentativa de jogada (objeto pequeno e descartável). */
    public record Resultado(boolean ok, String codigoErro, String mensagemErro,
                            Carta usada, Carta recebida,
                            boolean captura, int capLinha, int capColuna, char capPeca,
                            boolean fim, int vencedor, String motivo) {

        static Resultado erro(String codigo, String mensagem) {
            return new Resultado(false, codigo, mensagem, null, null, false, 0, 0, '.', false, 0, null);
        }
    }

    public static final int TAM = 5;

    private final char[][] tabuleiro = new char[TAM][TAM];
    private final Carta[][] maos = new Carta[3][2]; // índices 1 e 2 = jogadores (0 não usado)
    private Carta reserva;
    private int vez;
    private Fase fase = Fase.ESPERANDO;
    private int vencedor;
    private String motivoFim = "";

    // ---------------------------------------------------------------- ciclo de vida

    /** Prepara uma nova partida: posiciona peças, sorteia 5 cartas e dá a vez ao jogador 1. */
    public void iniciar() {
        for (char[] linha : tabuleiro) java.util.Arrays.fill(linha, '.');
        for (int c = 0; c < TAM; c++) {
            tabuleiro[0][c] = (c == 2) ? 'B' : 'b'; // jogador 2 (vermelho) em cima
            tabuleiro[4][c] = (c == 2) ? 'A' : 'a'; // jogador 1 (azul) embaixo
        }
        List<Carta> baralho = new ArrayList<>(Carta.TODAS);
        Collections.shuffle(baralho);
        maos[1][0] = baralho.get(0);
        maos[1][1] = baralho.get(1);
        maos[2][0] = baralho.get(2);
        maos[2][1] = baralho.get(3);
        reserva = baralho.get(4);
        vez = 1;
        vencedor = 0;
        motivoFim = "";
        fase = Fase.JOGANDO;
    }

    /** Volta ao estado de espera (um jogador saiu). Reaproveita os arrays existentes. */
    public void reiniciar() {
        for (char[] linha : tabuleiro) java.util.Arrays.fill(linha, '.');
        for (Carta[] m : maos) java.util.Arrays.fill(m, null); // libera referências às cartas
        reserva = null;
        vez = 0;
        vencedor = 0;
        motivoFim = "";
        fase = Fase.ESPERANDO;
    }

    /** Encerra a partida declarando um vencedor (ex.: desconexão do adversário). */
    public void encerrar(int vencedor, String motivo) {
        this.fase = Fase.FIM;
        this.vencedor = vencedor;
        this.motivoFim = motivo;
    }

    // ---------------------------------------------------------------- regras

    /**
     * Valida e executa uma jogada. Ordem das verificações (todas feitas no servidor):
     * fase, vez, limites, peça do jogador, carta do jogador, movimento da carta, casa de destino.
     */
    public Resultado jogar(int jogador, String idCarta, int lo, int co, int ld, int cd) {
        if (fase != Fase.JOGANDO) return Resultado.erro("PARTIDA_INATIVA", "A partida não está em andamento.");
        if (jogador != vez) return Resultado.erro("NAO_E_SUA_VEZ", "Não é a sua vez de jogar!");
        if (!dentro(lo, co) || !dentro(ld, cd)) return Resultado.erro("FORA_DO_TABULEIRO", "Posição fora do tabuleiro.");

        char peca = tabuleiro[lo][co];
        if (peca == '.' || donoDa(peca) != jogador) return Resultado.erro("PECA_INVALIDA", "Você deve mover uma peça sua.");

        int slot = -1;
        for (int i = 0; i < 2; i++) {
            if (maos[jogador][i] != null && maos[jogador][i].id().equals(idCarta)) slot = i;
        }
        if (slot < 0) return Resultado.erro("CARTA_INVALIDA", "Essa carta não pertence a você.");
        Carta carta = maos[jogador][slot];

        boolean permitido = false;
        for (int[] mov : carta.movimentosPara(jogador)) {
            if (lo + mov[0] == ld && co + mov[1] == cd) permitido = true;
        }
        if (!permitido) return Resultado.erro("MOVIMENTO_INVALIDO", "A carta " + carta.nome() + " não permite esse movimento.");

        char alvo = tabuleiro[ld][cd];
        if (alvo != '.' && donoDa(alvo) == jogador) return Resultado.erro("CASA_OCUPADA", "A casa de destino já tem uma peça sua.");

        // ---- jogada válida: atualiza o estado em lugar ----
        boolean captura = alvo != '.';
        tabuleiro[ld][cd] = peca;
        tabuleiro[lo][co] = '.';

        // troca de cartas: a carta usada vai para a reserva e a reserva antiga entra na mão
        Carta recebida = reserva;
        maos[jogador][slot] = reserva;
        reserva = carta;

        boolean fim = false;
        String motivo = null;
        if (captura && Character.toUpperCase(alvo) == alvo) { // capturou um Mestre
            fim = true;
            motivo = "MESTRE_CAPTURADO";
        } else if (Character.isUpperCase(peca) && ld == (jogador == 1 ? 0 : 4) && cd == 2) { // Mestre no templo
            fim = true;
            motivo = "TEMPLO";
        }
        if (fim) {
            fase = Fase.FIM;
            vencedor = jogador;
            motivoFim = motivo;
        } else {
            vez = (jogador == 1) ? 2 : 1;
        }
        return new Resultado(true, null, null, carta, recebida, captura, ld, cd, alvo, fim, fim ? jogador : 0, motivo);
    }

    private static boolean dentro(int l, int c) {
        return l >= 0 && l < TAM && c >= 0 && c < TAM;
    }

    private static int donoDa(char peca) {
        return Character.toLowerCase(peca) == 'a' ? 1 : 2;
    }

    // ---------------------------------------------------------------- leitura

    public int getVez() { return vez; }
    public Fase getFase() { return fase; }
    public int getVencedor() { return vencedor; }

    /**
     * Foto do estado para sincronizar os clientes (formato do protocolo):
     * STATE|vez|fase|mao1a,mao1b|mao2a,mao2b|reserva|25 casas (linha a linha)
     */
    public String serializar() {
        StringBuilder tab = new StringBuilder(25);
        for (char[] linha : tabuleiro) tab.append(linha);
        return Protocolo.STATE + "|" + vez + "|" + fase + "|"
                + id(maos[1][0]) + "," + id(maos[1][1]) + "|"
                + id(maos[2][0]) + "," + id(maos[2][1]) + "|"
                + id(reserva) + "|" + tab;
    }

    private static String id(Carta c) {
        return c == null ? "-" : c.id();
    }
}

