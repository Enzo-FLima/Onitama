package Enzo.lima;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;

public class ServidorOnitama extends WebSocketServer {

    // --- Memória Compartilhada ---
    private String[][] tabuleiro = new String[5][5];
    private int vez = 1; // 1 para jogador 1, 2 para jogador 2

    public ServidorOnitama(int porta) {
        super(new InetSocketAddress(porta));
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("Novo jogador conectado: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("Jogador desconectado: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket conn, String mensagem) {
        // Exclusão mútua garantida
        synchronized (this) {
            System.out.println("Mensagem recebida: " + mensagem);

            // A notação esperada tem 7 caracteres. Exemplo: 105b2c3
            // 1  -> Jogador
            // 05 -> ID Numérico da Carta (evita o erro das letras iguais)
            // b2 -> Origem
            // c3 -> Destino
            if (mensagem.length() == 7) {
                int jogadorDaMensagem = Character.getNumericValue(mensagem.charAt(0));
                String idCarta = mensagem.substring(1, 3); // Extrai os caracteres nas posições 1 e 2
                String origem = mensagem.substring(3, 5);  // Extrai as posições 3 e 4
                String destino = mensagem.substring(5, 7); // Extrai as posições 5 e 6

                // Verifica se é a vez do jogador que enviou a mensagem
                if (jogadorDaMensagem != vez) {
                    conn.send("ERRO: Não é a sua vez de jogar! \n          AGUARDE!       ");
                    return; // Interrompe o processamento
                }

                System.out.println("Sucesso: Jogador " + jogadorDaMensagem + " usou a carta " + idCarta +
                        " movendo a peça de " + origem + " para " + destino);

                // TODO: Adicionar futuramente a lógica de alterar a matriz 'tabuleiro'
                // e trocar as cartas entre os jogadores.

                // Passa a vez para o outro jogador
                vez = (vez == 1) ? 2 : 1;

                // Envia a jogada validada para todos os clientes atualizarem o frontend
                broadcast("JOGADA_OK:" + mensagem);
                System.out.println("Turno alterado. Agora é a vez do jogador: " + vez);

            } else {
                conn.send("ERRO: Formato de notação inválido.");
            }
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("Servidor Onitama ligado! Aguardando jogadores na porta " + getPort());
    }

    public static void main(String[] args) {
        ServidorOnitama servidor = new ServidorOnitama(8080);
        servidor.start();
    }
}