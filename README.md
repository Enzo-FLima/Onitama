Onitama em rede – Trabalho SO (Sistemas Operacionais)

Jogo: Onitama (tabuleiro 5×5, 2 jogadores) 
Linguagem: Java 21 (servidor) + HTML/CSS/JavaScript (cliente)
Alunos: Enzo Ferreira de Lima(RA: 25357031-2) e Hillian Gabriel Branco Basilio(RA: 25075697-2) 
Vídeo: _(colar link)_

Sobre o Jogo (Onitama): Onitama é um jogo de estratégia abstrata em turnos para 2 jogadores (Azul = Jogador 1 e Vermelho = Jogador 2), disputado em um tabuleiro 5×5 que representa um tatame de artes marciais. Cada jogador começa na sua fileira base com 5 peças: 1 Mestre (posicionado no centro, na casa do seu Templo) e 4 Discípulos (dois de cada lado do Mestre).

Diferente do xadrez, as peças não possuem movimentos fixos próprios: quem define o movimento são as Cartas de Estilo Marcial. No projeto foram implementadas 26 cartas (as 16 do jogo base + 10 da expansão Sensei's Path, como Tigre, Dragão, Sapo, Caranguejo, Fênix, etc.).

Dinâmica da Partida:

Sorteio inicial: No início da partida, o servidor sorteia 5 cartas dentre as 26 do baralho: entrega 2 cartas para o Jogador 1, 2 cartas para o Jogador 2 e deixa 1 carta na Reserva (ao lado do tabuleiro). As 5 cartas ficam abertas e visíveis para ambos os jogadores.
Realizando uma jogada: Na sua vez, o jogador escolhe 1 das suas 2 cartas e move 1 das suas peças (Mestre ou Discípulo) seguindo exatamente um dos deslocamentos ilustrados na carta escolhida em relação à posição atual da peça. As peças podem pular sobre outras peças, mas não podem parar em uma casa ocupada por uma peça da própria cor.
Captura: Se a peça terminar o movimento em uma casa ocupada por uma peça adversária, a peça inimiga é capturada e removida do tabuleiro.
Rotação de cartas: Após concluir o movimento, a carta que o jogador acabou de usar vai para a Reserva, e o jogador pega para a sua mão a carta que estava na Reserva. Assim, as 5 cartas circulam continuamente entre os dois jogadores ao longo de toda a partida (a carta que você usa agora poderá ser usada pelo oponente no futuro).

Condições de Vitória: Não existe empate no Onitama. Um jogador vence imediatamente de três formas:

Caminho da Pedra (MESTRE_CAPTURADO): Capturando o Mestre adversário.
Caminho do Riacho (TEMPLO): Levando o seu próprio Mestre até a casa do Templo adversário (casa central da linha inicial do oponente).
Desconexão (DESCONEXAO): Caso o adversário abandone/feche a conexão durante a partida em andamento (vitória por W.O.).

Como se Joga (Controles e Interface):

Perspectiva individual: Cada jogador abre o cliente no navegador. Ambos os jogadores enxergam sempre as suas próprias peças e as suas 2 cartas na parte inferior da tela (para o Jogador 2, o cliente gira automaticamente a visão do tabuleiro em 180°).
Passo 1 — Selecionar a carta: No seu turno, clique em uma das suas 2 cartas na parte inferior da tela (ela ficará destacada com uma borda dourada).
Passo 2 — Mover a peça: Arraste a peça desejada com o mouse e solte-a na casa de destino (Drag and Drop), ou clique na peça (ela ficará destacada em verde) e depois clique na casa de destino (suporte para telas touch/celular).
Feedback e Painel de Mensagens: No canto superior esquerdo há um painel em tempo real que informa de quem é a vez, exibe alertas caso o jogador tente fazer uma jogada inválida (ex.: jogar fora da vez ou fazer um movimento não permitido pela carta) e mostra o log das mensagens trocadas via WebSocket (▶ enviadas e ◀ recebidas). Ao final da partida, uma tela anuncia o vencedor e oferece o botão "Jogar novamente" para iniciar uma nova partida.

COMO E QUAIS INFORMAÇÕES SÃO TROCADAS ENTRE OS PROCESSOS?
1. Como é feita a comunicação entre os processos: A comunicação ocorre em uma arquitetura Cliente/Servidor envolvendo 3 processos independentes: 1 processo Servidor (backend em Java/Spring Boot com Tomcat embutido) e 2 processos Clientes (instâncias do navegador executando o front-end em HTML/JS). O canal de comunicação utilizado é o WebSocket (sobre socket TCP na porta 8080). O servidor atua como autoridade única da partida: os clientes não processam regras localmente; eles apenas enviam intenções de jogada e renderizam o estado validado pelo servidor.

Para a troca de mensagens, foi criada uma notação textual própria delimitada pelo caractere pipe (|), seguindo o padrão: CODIGO|campo1|campo2|...

As coordenadas do tabuleiro utilizam linha e coluna de 0 a 4 (onde a linha 0 é a base do Jogador 2 no topo e a linha 4 é a base do Jogador 1 embaixo).

2. Quais informações são trocadas (Protocolo de Mensagens):

A) Mensagens enviadas do Cliente para o Servidor:

MOVE|jogador|carta|linhaOrig|colOrig|linhaDest|colDest: Enviada quando o jogador escolhe uma carta e move uma peça. Exemplo: MOVE|1|TIGRE|4|2|2|2 (Jogador 1 solicita mover a peça da posição (4,2) para (2,2) usando a carta TIGRE).
RESTART|jogador: Enviada por qualquer jogador após o encerramento da partida para solicitar o início de um novo jogo. Exemplo: RESTART|1.
PING: Mensagem periódica de keep-alive para manter a conexão ativa.

B) Mensagens enviadas do Servidor para os Clientes:

CONNECT|jogador: Informa ao cliente recém-conectado qual é o seu número (1 para Azul ou 2 para Vermelho). Exemplo: CONNECT|1.
WAIT|texto: Informa ao primeiro jogador que aguarde a conexão do adversário. Exemplo: WAIT|Aguardando o adversário....
START|primeiroJogador: Notifica ambos os processos que a sala atingiu 2 jogadores e a partida começou, indicando de quem é o turno inicial. Exemplo: START|1.
CARDS|jogador|carta1,carta2|reserva: Informa as cartas sorteadas no início da partida para a mão do jogador e a carta de reserva. Exemplo: CARDS|1|ENGUIA,LOUVA_DEUS|GANSO.
TURN|jogador: Controla e sincroniza a variável inteira vez (1 ou 2) em ambos os processos, liberando o turno para o jogador da vez e bloqueando o outro. Exemplo: TURN|2.
MOVED|jogador|carta|linhaOrig|colOrig|linhaDest|colDest: Confirma para ambos os clientes que a jogada foi validada e executada no servidor. Exemplo: MOVED|1|TIGRE|4|2|2|2.
CAPTURE|jogador|linha|coluna|peca: Enviada quando um movimento resulta na captura de uma peça inimiga (D = Discípulo, M = Mestre). Exemplo: CAPTURE|1|2|2|D.
SWAP|jogador|cartaUsada|cartaRecebida: Notifica a rotação de cartas entre a mão do jogador e a reserva após o movimento. Exemplo: SWAP|1|TIGRE|GANSO.
STATE|vez|fase|mao1|mao2|reserva|tabuleiro: Envia um retrato completo (snapshot) da memória compartilhada do servidor para garantir sincronização total entre os clientes. Contém a variável vez (1 ou 2), a fase (ESPERANDO, JOGANDO ou FIM), os pares de cartas de cada jogador, a carta reserva e uma string de 25 caracteres representando as casas do tabuleiro 5×5 (. = casa vazia, a/b = discípulo do jogador 1/2, A/B = Mestre do jogador 1/2). Exemplo: STATE|2|JOGANDO|GANSO,ENGUIA|SAPO,ELEFANTE|TIGRE|bbBbb...............aaAaa.
WIN|jogador|motivo e LOSE|jogador|motivo: Informam o vencedor e o perdedor ao fim do jogo, junto com a causa (MESTRE_CAPTURADO, TEMPLO ou DESCONEXAO). Exemplo: WIN|1|TEMPLO.
OPPONENT_LEFT|jogador: Avisa o jogador remanescente que o adversário se desconectou. Exemplo: OPPONENT_LEFT|2.
ERROR|codigo|mensagem: Enviada apenas ao cliente que tentou realizar uma ação inválida, mantendo a variável vez inalterada. Códigos tratados: NAO_E_SUA_VEZ, PECA_INVALIDA, CARTA_INVALIDA, MOVIMENTO_INVALIDO, FORA_DO_TABULEIRO, CASA_OCUPADA, PARTIDA_INATIVA, JOGADOR_INVALIDO, FORMATO_INVALIDO, COMANDO_DESCONHECIDO e SALA_CHEIA. Exemplo: ERROR|NAO_E_SUA_VEZ|Não é a sua vez de jogar!.

QUAL É O DADO DO JOGO QUE É COMPARTILHADO ENTRE OS JOGADORES?

O dado compartilhado entre os jogadores é a instância única da classe EstadoDoJogo mantida na memória do servidor (dentro do singleton PartidaService), além da tabela de sessões ativas (String[] jogadores = new String[3]) e do catálogo imutável de cartas (Carta.TODAS).

Especificamente, a memória compartilhada da partida armazena as seguintes estruturas de dados:

Tabuleiro (char[5][5] tabuleiro): Matriz 5×5 alocada uma única vez na memória que representa as 25 casas do jogo, onde '.' indica casa vazia, 'a' e 'A' representam os Discípulos e o Mestre do Jogador 1 (Azul), e 'b' e 'B' representam os Discípulos e o Mestre do Jogador 2 (Vermelho).
Mãos dos Jogadores (Carta[3][2] maos): Matriz que guarda as referências para as 2 cartas atuais do Jogador 1 (maos[1][0..1]) e as 2 cartas do Jogador 2 (maos[2][0..1]).
Carta de Reserva (Carta reserva): Referência para a 5ª carta (central/lateral) que circula entre os dois jogadores a cada turno.
Controle de Turno (int vez): Variável inteira (1 ou 2) que define de qual jogador é a vez de jogar no momento.
Estado/Fase da Partida (Fase fase, int vencedor, String motivoFim): Variáveis que controlam o ciclo de vida da partida (ESPERANDO, JOGANDO ou FIM), quem venceu e o motivo do término.

COMO É FEITO O GERENCIAMENTO DA EXCLUSÃO MÚTUA?

O gerenciamento da exclusão mútua é feito no servidor por meio de um Mutex explícito utilizando a classe ReentrantLock (private final ReentrantLock mutex = new ReentrantLock()) na camada de serviço (PartidaService).

Por que a exclusão mútua é necessária no jogo: Como o servidor WebSocket (Tomcat embutido do Spring Boot) é multithread, cada conexão de jogador é processada concorrentemente em uma thread distinta do pool do servidor. Sem controle de concorrência, se os dois jogadores enviassem mensagens simultaneamente (ex.: dois comandos MOVE, um MOVE no mesmo instante de uma desconexão, ou duas conexões entrando ao mesmo tempo), ocorreria condição de corrida (race condition), podendo corromper a matriz do tabuleiro, trocar cartas de forma inconsistente ou alterar a variável vez simultaneamente.

Como foi implementado: Todas as operações que leem ou modificam a região crítica (estado e vetor jogadores) — isto é, os métodos conectar(), desconectar(), mover(), reiniciar() e erro() — seguem estritamente o protocolo de entrada e saída da seção crítica com lock() e unlock() dentro de um bloco try / finally.
