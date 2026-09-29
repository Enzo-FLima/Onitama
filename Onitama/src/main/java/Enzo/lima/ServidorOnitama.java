package Enzo.lima;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;

public class ServidorOnitama extends WebSocketServer {

    // --- MEMÓRIA COMPARTILHADA --- //
    private String[][] tabuleiro = new String[5][5];
    private int vez = 1; // Controla o turno (1 = Jogador 1, 2 = Jogador 2)

    public ServidorOnitama(int porta) {
        super(new InetSocketAddress(porta));
    }

    // --- MÉTODOS DE COMUNICAÇÃO --- //
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("Novo jogador conectado: " + conn.getRemoteSocketAddress());
        conn.send("Bem-vindo ao Onitama! O jogo vai começar.");
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("Jogador desconectado: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket conn, String mensagem) {
        System.out.println("Mensagem recebida: " + mensagem);

        // --- EXCLUSÃO MÚTUA E NOTAÇÃO ---
        // O bloco synchronized garante que se os dois jogadores mandarem jogadas
        // exatamente ao mesmo tempo, o servidor vai processar uma de cada vez.
        synchronized (this) {
            broadcast("Jogada processada: " + mensagem + " | Agora é a vez do jogador: " + (vez == 1 ? 2 : 1));
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("Ocorreu um erro no servidor:");
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("Servidor Onitama ligado! Aguardando jogadores na porta " + getPort());
    }

    // --- MÉTODO PRINCIPAL (MAIN) ---//
    public static void main(String[] args) {
        // Porta recomendada para testes locais
        int porta = 8080;
        ServidorOnitama servidor = new ServidorOnitama(porta);
        servidor.start();
    }
}
