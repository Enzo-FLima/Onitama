package Enzo.lima;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada do servidor Onitama (Spring Boot).
 *
 * Organização do backend no padrão MVC:
 * <pre>
 *  model/       Carta, EstadoDoJogo        -> dados e regras do jogo
 *  view/        MensagemView, Protocolo    -> representação do model nas mensagens do protocolo
 *  controller/  OnitamaController          -> recebe as mensagens do WebSocket e delega
 *  service/     PartidaService             -> vagas, turno, desconexão e exclusão mútua
 *  config/      WebSocketConfig            -> registra o endpoint WebSocket
 * </pre>
 *
 * Executar: {@code mvn spring-boot:run} (porta 8080; outra porta: {@code --server.port=9090}).
 */
@SpringBootApplication
public class OnitamaApplication {

    public static void main(String[] args) {
        SpringApplication.run(OnitamaApplication.class, args);
    }
}

