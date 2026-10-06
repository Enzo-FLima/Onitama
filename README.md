# Onitama em rede – Trabalho 02 (Sistemas Operacionais)

**Jogo:** Onitama (tabuleiro 5×5, 2 jogadores) · **Linguagem:** Java 21 (servidor) + HTML/CSS/JavaScript (cliente)
**Alunos:** _(preencher nomes da dupla)_ · **Vídeo:** _(colar link)_

## 1. Como o jogo funciona
Cada jogador tem 1 **Mestre** e 4 **Discípulos**. Existem 5 cartas sorteadas: 2 para cada jogador e 1 de reserva.
Na sua vez o jogador escolhe **uma das suas cartas** e move **uma peça** conforme o desenho da carta
(o desenho é visto "de frente" por quem joga). Depois da jogada a carta usada vai para a reserva e o jogador
pega a carta que estava na reserva. Cair numa casa com peça adversária **captura** a peça.

**Vitória:** (a) capturar o Mestre adversário ou (b) levar o seu Mestre ao templo adversário
(casa central da linha inicial do oponente). Também vence quem tiver o adversário desconectado.
O jogador 1 (azul) começa. Ambos veem as suas peças embaixo (o jogador 2 vê o tabuleiro girado 180°).

## 2. Arquitetura e comunicação entre processos

```text
 CLIENTE 1 (navegador)          SERVIDOR (processo Java)          CLIENTE 2 (navegador)
  index.html + JS    <-- socket -->  ServidorOnitama  <-- socket -->   index.html + JS
                                      │  EstadoDoJogo (memória única)
                                      │  Mutex (ReentrantLock)
```

* Canal: **WebSocket** (socket TCP) na porta **8080** – biblioteca `Java-WebSocket`.
* O servidor é a **única autoridade**: valida a jogada, altera o estado e envia mensagens. O cliente só
  envia `MOVE` e desenha o `STATE` recebido, por isso os dois nunca ficam dessincronizados.
* São 3 processos independentes (servidor + 2 navegadores), que só se comunicam por mensagens.

Arquivos:

| Arquivo | Papel |
|---|---|
| `Onitama/src/main/java/Enzo/lima/ServidorOnitama.java` | Sockets, mutex, vagas de jogador, controle de turno, desconexão |
| `.../EstadoDoJogo.java` | Estado central da partida (memória) e regras/validação |
| `.../Carta.java` | Catálogo imutável das 26 cartas e seus movimentos |
| `.../Protocolo.java` | Constantes da notação de mensagens |
| `FrontEnd/index.html` | Interface e cliente (JS) |

## 3. Protocolo de mensagens (notação própria)
Formato: `CODIGO|campo|campo|...` (separador `|`). Coordenadas: `linha` e `coluna` de 0 a 4 **no tabuleiro
do servidor** (linha 0 = topo/vermelho, linha 4 = base/azul). Cartas: id em maiúsculas (`TIGRE`, `DRAGAO`, `LOUVA_DEUS`…).

### Cliente → Servidor
| Código | Quem envia | Dados | Significado / quando | Exemplo |
|---|---|---|---|---|
| `MOVE` | jogador da vez | `jogador\|carta\|linhaOrig\|colOrig\|linhaDest\|colDest` | Pedido de jogada, ao soltar a peça com uma carta selecionada. O servidor valida. | `MOVE\|1\|TIGRE\|4\|2\|2\|2` |
| `RESTART` | qualquer um, após o fim | `jogador` | Pede nova partida. | `RESTART\|1` |

### Servidor → Cliente
| Código | Para | Dados | Significado / quando | Exemplo |
|---|---|---|---|---|
| `CONNECT` | quem conectou | `jogador` | Informa se você é o jogador 1 ou 2 (ao conectar). | `CONNECT\|2` |
| `WAIT` | jogador sozinho | `texto` | Aguardando adversário. | `WAIT\|Aguardando o adversário...` |
| `START` | ambos | `primeiroJogador` | Partida começou (2 jogadores presentes). | `START\|1` |
| `CARDS` | cada jogador | `jogador\|carta1,carta2\|reserva` | Cartas recebidas no início. | `CARDS\|1\|ENGUIA,LOUVA_DEUS\|GANSO` |
| `TURN` | ambos | `jogador` | De quem é a vez (variável `vez`). | `TURN\|2` |
| `MOVED` | ambos | `jogador\|carta\|lo\|co\|ld\|cd` | Jogada **já validada** e executada. | `MOVED\|1\|TIGRE\|4\|2\|2\|2` |
| `CAPTURE` | ambos | `jogador\|linha\|coluna\|peca` (`D` discípulo, `M` mestre) | Peça capturada na jogada. | `CAPTURE\|1\|2\|2\|D` |
| `SWAP` | ambos | `jogador\|cartaUsada\|cartaRecebida` | Troca de cartas com a reserva. | `SWAP\|1\|TIGRE\|GANSO` |
| `STATE` | ambos | `vez\|fase\|mao1\|mao2\|reserva\|tabuleiro` | **Foto completa** do estado (sincronização). `fase`: `ESPERANDO`/`JOGANDO`/`FIM`. `tabuleiro` = 25 caracteres linha a linha: `.` vazio, `a`/`b` discípulo do jogador 1/2, `A`/`B` mestre. | `STATE\|2\|JOGANDO\|GANSO,ENGUIA\|SAPO,ELEFANTE\|TIGRE\|bbBbb.....` |
| `WIN` | vencedor | `jogador\|motivo` | Vitória (`MESTRE_CAPTURADO`, `TEMPLO`, `DESCONEXAO`). | `WIN\|1\|TEMPLO` |
| `LOSE` | perdedor | `jogador\|motivo` | Derrota. | `LOSE\|2\|TEMPLO` |
| `OPPONENT_LEFT` | quem ficou | `jogador` | Adversário desconectou. | `OPPONENT_LEFT\|2` |
| `ERROR` | só quem errou | `codigo\|mensagem` | Jogada/mensagem inválida; **a vez não muda**. Códigos: `NAO_E_SUA_VEZ`, `PECA_INVALIDA`, `CARTA_INVALIDA`, `MOVIMENTO_INVALIDO`, `FORA_DO_TABULEIRO`, `CASA_OCUPADA`, `PARTIDA_INATIVA`, `JOGADOR_INVALIDO`, `FORMATO_INVALIDO`, `COMANDO_DESCONHECIDO`, `SALA_CHEIA`. | `ERROR\|NAO_E_SUA_VEZ\|Não é a sua vez de jogar!` |

Empate não existe nas regras do Onitama, por isso não há mensagem de empate.

**Sequência de uma jogada válida do jogador 1:**
`MOVE` (J1→S) → servidor valida → `MOVED`, [`CAPTURE`], `SWAP`, `TURN|2` (ou `WIN`/`LOSE`), `STATE` (S→J1 e J2).
Cada cliente interpreta `STATE` redesenhando tabuleiro e cartas; `TURN` libera/bloqueia a vez; `ERROR` aparece no painel.

### Validações feitas pelo servidor (nesta ordem)
1. partida em andamento; 2. é a vez do jogador (e o número na mensagem é o dono da conexão);
3. destino/origem dentro do tabuleiro; 4. peça de origem pertence ao jogador;
5. carta pertence à mão do jogador; 6. deslocamento existe na carta (invertido para o jogador 2);
7. destino não tem peça do próprio jogador; 8. captura e condição de vitória.

## 4. Gerenciamento de memória
O estado vive em **uma única instância** de `EstadoDoJogo` (no servidor):
`tabuleiro char[5][5]`, `maos Carta[3][2]`, `reserva`, `vez`, `fase`.
* **Alocada** uma vez (construtor / campo do servidor). As 26 cartas são alocadas uma vez (`static final`, somente leitura).
* **Atualizada** in-place a cada jogada (sem cópias do estado); mãos guardam apenas referências às cartas.
* **Compartilhada** entre as threads (uma por conexão) protegida pelo mutex.
* **Liberada**: `reiniciar()` zera as referências e reaproveita os arrays; ao encerrar, a JVM libera tudo.
Os clientes **não guardam estado próprio da partida**: só o último `STATE` recebido para desenhar.

## 5. Exclusão mútua
`ReentrantLock mutex` em `ServidorOnitama`. A biblioteca chama `onOpen`/`onMessage`/`onClose` em threads
diferentes; sem proteção, dois `MOVE` simultâneos poderiam alterar tabuleiro, cartas e `vez` ao mesmo tempo.
Região crítica (protege tabuleiro, mãos, reserva, `vez`, `fase` e a tabela de jogadores conectados):
```text
LOCK → verifica a vez → valida → altera tabuleiro/cartas/vez → envia atualização → UNLOCK (finally)
```
Enviar dentro do lock evita que mensagens de jogadas diferentes se misturem. Testado enviando
dois `MOVE` simultâneos: apenas um é aceito, o outro recebe `ERROR`.

## 6. Tratamento de desconexão
`onClose` libera a vaga; se havia adversário ele recebe `OPPONENT_LEFT` e, se a partida estava em andamento,
`WIN|n|DESCONEXAO`; o estado é reiniciado e ele volta a `WAIT`. Um novo jogador pode entrar na vaga livre
e a partida recomeça. Um 3º jogador recebe `ERROR|SALA_CHEIA`. Falhas de envio são capturadas.

## 7. Como executar
Requisitos: Java 21 (e Maven, ou IntelliJ).
1. **Servidor** – uma das opções:
   * IntelliJ: abrir `Onitama/` e executar `ServidorOnitama.main`;
   * Terminal: `cd Onitama` → `mvn compile exec:java`.
   Deve aparecer `Servidor Onitama ligado! ... porta 8080`.
2. **Jogadores** – abrir `FrontEnd/index.html` em **duas janelas/abas** do navegador (a 1ª conectada é o
   jogador 1 azul, a 2ª o jogador 2 vermelho). Em outro computador da rede:
   `index.html?server=ws://IP_DO_SERVIDOR:8080`.
3. **Jogar:** clique numa das suas cartas (fica com borda dourada) e **arraste uma peça sua** para a casa de destino.
   O painel à esquerda mostra de quem é a vez e as mensagens do protocolo (`▶` enviadas, `◀` recebidas).

## 8. Requisitos do trabalho × onde foram atendidos
| Requisito | Onde |
|---|---|
| ≥ 2 usuários / comunicação real entre processos | 2 navegadores + servidor via WebSocket |
| Sockets | `ServidorOnitama` (WebSocketServer) e `WebSocket` do JS |
| Notação própria de mensagens | Seção 3, `Protocolo.java` |
| Controle da vez por mensagens | `vez` no servidor, `TURN`, `ERROR|NAO_E_SUA_VEZ` |
| Gerenciamento de memória | Seção 4, comentários em `EstadoDoJogo` |
| Exclusão mútua | Seção 5, `mutex` em `ServidorOnitama` |
| Validação das jogadas, captura, vitória | `EstadoDoJogo.jogar` |
| Sincronização | `STATE` após cada jogada |
| Desconexão | Seção 6 |

## 9. Roteiro sugerido para o vídeo (~1 minuto)
1. (0:00–0:10) Mostrar o servidor ligado no terminal e abrir duas janelas lado a lado ("Você é o jogador 1/2").
2. (0:10–0:25) Jogador 2 tenta jogar fora da vez → mensagem de erro. Jogador 1 escolhe carta, move: as duas telas atualizam; mostrar o painel com `MOVE`/`MOVED`/`SWAP`/`TURN`.
3. (0:25–0:40) Jogador 2 responde; mostrar jogada inválida (carta não permite) → `ERROR` e a vez não muda; fazer uma captura.
4. (0:40–0:52) Terminar com uma vitória (capturar Mestre ou chegar ao templo) → tela "Você venceu".
5. (0:52–1:00) Fechar uma janela: o outro jogador vê "adversário desconectou". Citar mutex e `STATE` na fala.

## 10. Entrega
* **Sem executável:** compactar `FrontEnd/`, `Onitama/src`, `Onitama/pom.xml` e este `README.md` (sem `target/`).
* **Com executável:** gerar com `mvn package` e incluir `target/` (ou o `.class`/`.jar` equivalente) junto.

