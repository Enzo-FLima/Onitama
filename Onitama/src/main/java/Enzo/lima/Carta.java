package Enzo.lima;

import java.util.List;

/**
 * Carta de movimento do Onitama.
 *
 * Os movimentos são pares {dLinha, dColuna} vistos da perspectiva do DONO da carta:
 * dLinha = -1 significa "para frente". Para o jogador 2 (que joga de cima para baixo)
 * o servidor inverte o sinal dos dois valores (veja {@link #movimentosPara(int)}).
 *
 * MEMÓRIA: o catálogo é criado uma única vez (static final) e compartilhado,
 * somente leitura, por todas as partidas. Ninguém copia ou altera estas cartas;
 * o estado do jogo guarda apenas referências para elas.
 */
public record Carta(String id, String nome, int[][] movimentos) {

    private static Carta c(String id, String nome, int[]... mov) {
        return new Carta(id, nome, mov);
    }

    private static int[] m(int dl, int dc) {
        return new int[]{dl, dc};
    }

    /** Catálogo completo (16 clássicas + 10 da expansão Sensei's Path presentes no front-end). */
    public static final List<Carta> TODAS = List.of(
            // ---- Jogo base ----
            c("SAPO", "Sapo", m(0, -2), m(-1, -1), m(1, 1)),
            c("DRAGAO", "Dragão", m(-1, -2), m(-1, 2), m(1, -1), m(1, 1)),
            c("SERPENTE", "Serpente do Mar", m(-1, 0), m(0, 2), m(1, -1)),
            c("TIGRE", "Tigre", m(-2, 0), m(1, 0)),
            c("CARANGUEJO", "Caranguejo", m(-1, 0), m(0, -2), m(0, 2)),
            c("MACACO", "Macaco", m(-1, -1), m(-1, 1), m(1, -1), m(1, 1)),
            c("GARCA", "Garça", m(-1, 0), m(1, -1), m(1, 1)),
            c("GALO", "Galo", m(-1, 1), m(0, -1), m(0, 1), m(1, -1)),
            c("BOI", "Boi", m(-1, 0), m(0, 1), m(1, 0)),
            c("CAVALO", "Cavalo", m(-1, 0), m(0, -1), m(1, 0)),
            c("ENGUIA", "Enguia", m(-1, -1), m(0, 1), m(1, -1)),
            c("ELEFANTE", "Elefante", m(-1, -1), m(-1, 1), m(0, -1), m(0, 1)),
            c("LOUVA_DEUS", "Louva-a-Deus", m(-1, -1), m(-1, 1), m(1, 0)),
            c("JAVALI", "Javali", m(-1, 0), m(0, -1), m(0, 1)),
            c("COELHO", "Coelho", m(-1, 1), m(0, 2), m(1, -1)),
            c("GANSO", "Ganso", m(-1, -1), m(0, -1), m(0, 1), m(1, 1)),
            // ---- Expansão ----
            c("GIRAFA", "Girafa", m(0, -2), m(0, 2), m(1, 0)),
            c("FENIX", "Fênix", m(-1, -1), m(-1, 1), m(0, -2), m(0, 2)),
            c("TARTARUGA", "Tartaruga", m(0, -2), m(0, 2), m(1, -1), m(1, 1)),
            c("CACHORRO", "Cachorro", m(-1, -1), m(0, -1), m(1, -1)),
            c("RATO", "Rato", m(-1, 0), m(0, -1), m(1, 1)),
            c("COBRA", "Cobra", m(-1, 1), m(0, -1), m(1, 1)),
            c("LONTRA", "Lontra", m(-1, -1), m(0, 2), m(1, 1)),
            c("PANDA", "Panda", m(-1, 0), m(-1, 1), m(1, -1)),
            c("URSO", "Urso", m(-1, -1), m(-1, 0), m(1, 1)),
            c("RAPOSA", "Raposa", m(-1, 1), m(0, 1), m(1, 1))
    );

    /** Busca uma carta pelo identificador (ex.: "TIGRE"); retorna null se não existir. */
    public static Carta porId(String id) {
        for (Carta carta : TODAS) {
            if (carta.id.equals(id)) return carta;
        }
        return null;
    }

    /**
     * Movimentos reais no tabuleiro para o jogador informado.
     * Jogador 1 (azul, embaixo) anda para linhas menores; jogador 2 (vermelho, em cima),
     * para linhas maiores - por isso o sinal é invertido.
     */
    public int[][] movimentosPara(int jogador) {
        int s = (jogador == 1) ? 1 : -1;
        int[][] r = new int[movimentos.length][2];
        for (int i = 0; i < movimentos.length; i++) {
            r[i][0] = movimentos[i][0] * s;
            r[i][1] = movimentos[i][1] * s;
        }
        return r;
    }
}

