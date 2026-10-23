package Enzo.lima.config;

import Enzo.lima.controller.OnitamaController;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registra o {@link OnitamaController} como endpoint WebSocket.
 *
 * Caminhos aceitos (compatíveis com o front-end, que não precisou mudar):
 *  - "/"   : conexão direta, ex.: ws://localhost:8080 (arquivo local / rede local);
 *  - "/ws" : atrás do proxy (Caddy), ex.: wss://meudominio.com/ws.
 * Origens liberadas porque o index.html pode ser aberto como arquivo (Origin "null") ou por outro host.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final OnitamaController controller;

    public WebSocketConfig(OnitamaController controller) {
        this.controller = controller;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(controller, "/", "/ws").setAllowedOrigins("*");
    }
}

