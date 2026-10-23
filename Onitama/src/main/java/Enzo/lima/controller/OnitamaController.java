package Enzo.lima.controller;

import Enzo.lima.service.PartidaService;
import Enzo.lima.view.Protocolo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CONTROLLER - Porta de entrada dos clientes (arquitetura CLIENTE/SERVIDOR sobre sockets).
 *
 * <pre>
 *  CLIENTE 1 (navegador) <-- WebSocket --> SERVIDOR <-- WebSocket --> CLIENTE 2 (navegador)
 *
 *  OnitamaController (controller) -> PartidaService (service) -> EstadoDoJogo (model)
 *                                 \-> MensagemView (view) formata as respostas
 * </pre>
 *
 * Responsabilidades: aceitar/encerrar conexões, interpretar o texto recebido (notação de
 * {@link Protocolo}) e entregar as mensagens geradas pelo service ao socket certo.
 * Nenhuma regra de jogo fica aqui.
 */
@Controller
public class OnitamaController extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(OnitamaController.class);

    private final PartidaService partida;

    /** Conexões abertas, por id. */
    private final Map<String, WebSocketSession> sessoes = new ConcurrentHashMap<>();

    /** Canal de saída entregue ao service: id da conexão -> socket. */
    private final PartidaService.Saida saida = this::enviar;

    public OnitamaController(PartidaService partida) {
        this.partida = partida;
    }

    // ================================================================ conexão

    @Override
    public void afterConnectionEstablished(WebSocketSession sessao) throws Exception {
        sessoes.put(sessao.getId(), sessao);
        log.info("Nova conexão: {}", sessao.getRemoteAddress());
        if (partida.conectar(sessao.getId(), saida) == 0) {
            sessao.close(CloseStatus.POLICY_VIOLATION); // sala cheia
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession sessao, CloseStatus status) {
        sessoes.remove(sessao.getId());
        partida.desconectar(sessao.getId(), saida);
    }

    @Override
    public void handleTransportError(WebSocketSession sessao, Throwable erro) {
        log.warn("Erro de comunicação: {}", erro.toString());
    }

    // ================================================================ mensagens

    @Override
    protected void handleTextMessage(WebSocketSession sessao, TextMessage mensagem) {
        String texto = mensagem.getPayload().trim();
        if (!Protocolo.PING.equals(texto)) log.info("Recebido: {}", texto);

        String id = sessao.getId();
        String[] c = texto.split("\\|", -1);
        try {
            switch (c[0]) {
                case Protocolo.MOVE -> {
                    if (c.length != 7) throw new ArrayIndexOutOfBoundsException();
                    partida.mover(id, Integer.parseInt(c[1]), c[2],
                            Integer.parseInt(c[3]), Integer.parseInt(c[4]),
                            Integer.parseInt(c[5]), Integer.parseInt(c[6]), saida);
                }
                case Protocolo.RESTART -> partida.reiniciar(id, saida);
                case Protocolo.PING -> { } // keep-alive: evita que proxies derrubem conexões ociosas
                default -> partida.erro(id, "COMANDO_DESCONHECIDO", "Comando inválido: " + c[0], saida);
            }
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            partida.erro(id, "FORMATO_INVALIDO",
                    "Formato esperado: MOVE|jogador|carta|linhaOrigem|colOrigem|linhaDestino|colDestino", saida);
        }
    }

    /** Chamado pelo service DENTRO da região crítica, por isso os envios nunca se misturam. */
    private void enviar(String id, String msg) {
        WebSocketSession sessao = sessoes.get(id);
        if (sessao == null || !sessao.isOpen()) return;
        try {
            sessao.sendMessage(new TextMessage(msg));
            log.info("Enviado   : {}", msg);
        } catch (Exception e) { // socket caiu no meio do envio; afterConnectionClosed cuidará da limpeza
            log.warn("Falha ao enviar ({})", e.getClass().getSimpleName());
        }
    }
}

