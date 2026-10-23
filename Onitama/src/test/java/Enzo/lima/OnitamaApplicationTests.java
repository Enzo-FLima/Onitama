package Enzo.lima;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Sobe o servidor numa porta aleatória e conversa com ele como dois navegadores. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OnitamaApplicationTests {

    @LocalServerPort
    int porta;

    /** Cliente de teste que guarda as mensagens recebidas numa fila. */
    static class Cliente extends TextWebSocketHandler {
        final BlockingQueue<String> recebidas = new LinkedBlockingQueue<>();
        WebSocketSession sessao;

        @Override
        protected void handleTextMessage(WebSocketSession s, TextMessage m) {
            recebidas.add(m.getPayload());
        }

        /** Espera a próxima mensagem com o código informado (descarta as anteriores). */
        String esperar(String codigo) throws InterruptedException {
            while (true) {
                String m = recebidas.poll(5, TimeUnit.SECONDS);
                assertNotNull(m, "não chegou " + codigo);
                if (m.startsWith(codigo + "|") || m.equals(codigo)) return m;
            }
        }

        void enviar(String m) throws Exception {
            sessao.sendMessage(new TextMessage(m));
        }
    }

    private Cliente conectar(String caminho) throws Exception {
        Cliente c = new Cliente();
        c.sessao = new StandardWebSocketClient()
                .execute(c, "ws://localhost:" + porta + caminho).get(5, TimeUnit.SECONDS);
        return c;
    }

    @Test
    void partidaCompletaPeloSocket() throws Exception {
        Cliente j1 = conectar("/");
        assertEquals("CONNECT|1", j1.esperar("CONNECT"));
        j1.esperar("WAIT");

        Cliente j2 = conectar("/ws");
        assertEquals("CONNECT|2", j2.esperar("CONNECT"));
        assertEquals("START|1", j1.esperar("START"));
        assertEquals("START|1", j2.esperar("START"));
        assertTrue(j1.esperar("CARDS").startsWith("CARDS|1|"));
        assertEquals("TURN|1", j2.esperar("TURN"));
        String estado = j1.esperar("STATE");
        assertTrue(estado.endsWith("bbBbb...............aaAaa"), estado);

        // jogador 2 fora da vez
        j2.enviar("MOVE|2|TIGRE|0|0|1|0");
        assertTrue(j2.esperar("ERROR").startsWith("ERROR|NAO_E_SUA_VEZ"));

        // comando desconhecido e formato inválido
        j1.enviar("XYZ");
        assertTrue(j1.esperar("ERROR").startsWith("ERROR|COMANDO_DESCONHECIDO"));
        j1.enviar("MOVE|1|x");
        assertTrue(j1.esperar("ERROR").startsWith("ERROR|FORMATO_INVALIDO"));

        // terceiro jogador é recusado
        Cliente j3 = conectar("/");
        assertTrue(j3.esperar("ERROR").startsWith("ERROR|SALA_CHEIA"));

        // desconexão no meio da partida: o adversário vence por W.O.
        j1.sessao.close();
        assertEquals("OPPONENT_LEFT|1", j2.esperar("OPPONENT_LEFT"));
        assertEquals("WIN|2|DESCONEXAO", j2.esperar("WIN"));
        j2.esperar("WAIT");
        j2.sessao.close();
    }
}

