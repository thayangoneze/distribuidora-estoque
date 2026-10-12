# Distribuidora de bebidas — controle de estoque

Primeira etapa do sistema da Distribuidora de Bebidas do Gaúcho. Painel web e API em Java 17, Spring Boot e PostgreSQL para cadastro, entradas e saídas, consulta de saldo e sugestão de reposição.

## Executar

1. Instale Java 17, Maven e PostgreSQL. Crie o banco `distribuidora`.
2. Configure `DB_URL`, `DB_USER` e `DB_PASSWORD` se não usar os padrões de desenvolvimento definidos em `application.properties`.
3. Execute `mvn spring-boot:run` na raiz do projeto. Abra `http://localhost:8080` para usar o painel. A API estará em `http://localhost:8080/api`.

Exemplo de uso:

```bash
curl -X POST http://localhost:8080/api/produtos -H 'Content-Type: application/json' -d '{"nome":"Refrigerante 2 L","saldo":24,"estoqueSeguranca":10,"diasEntrega":[2,5]}'
curl -X POST http://localhost:8080/api/produtos/1/movimentacoes -H 'Content-Type: application/json' -d '{"tipo":"ENTRADA","quantidade":40}'
curl -X POST http://localhost:8080/api/produtos/1/movimentacoes -H 'Content-Type: application/json' -d '{"tipo":"SAIDA","quantidade":8}'
curl http://localhost:8080/api/produtos
curl 'http://localhost:8080/api/reposicao?dias=30'
```

A média diária usa saídas no período escolhido (30, 60 ou 90 dias), divididas pelo número de dias. O ponto de reposição é `ceil(média × prazo de entrega) + estoque de segurança`. Quando o saldo está nesse ponto ou abaixo dele, a quantidade proposta cobre prazo de entrega e dias adicionais configurados. Produtos sem saídas nos últimos 60 dias recebem `baixoGiro=true` e sugestão zero. Essas contas são estimativas determinísticas; não há modelo de IA treinado nesta etapa.

## Estado atual

- Implementados: painel web responsivo para cadastro básico, consulta de saldos, entradas e saídas, alerta visual de estoque crítico e sugestão com bloqueio para itens sem saída há 60 dias.
- Pendentes: múltiplos usuários e perfis, ajuste/confirmação de ordem de compra, portal atacadista, reserva e agendamento. O painel e a API atuais são apenas para desenvolvimento local e usam uma credencial provisória para desenvolvimento.
- A criação automática das tabelas (`ddl-auto=update`) é provisória; antes de produção, substituir por migrações versionadas.

Consulte a documentação do projeto para os requisitos RF-01 a RF-07, regras RN-01 a RN-03 e casos de uso UC-01 a UC-10.

## Organização do código

| Caminho | Responsabilidade |
| --- | --- |
| `src/main/java/br/com/distribuidora/EstoqueApplication.java` | Iniciar o Spring Boot |
| `src/main/java/br/com/distribuidora/controller/` | Receber as requisições da interface e expor a API |
| `src/main/java/br/com/distribuidora/service/` | Validar movimentações e calcular reposição |
| `src/main/java/br/com/distribuidora/model/` | Entidades Produto e Movimentacao persistidas no banco |
| `src/main/java/br/com/distribuidora/repository/` | Consultas e gravação no PostgreSQL |
| `src/main/java/br/com/distribuidora/dto/` | Dados de entrada e resultado da análise |
| `src/main/resources/static/index.html` | Estrutura da tela |
| `src/main/resources/static/css/` | Aparência e adaptação a celulares |
| `src/main/resources/static/js/` | Interação da tela com a API |
| `src/main/resources/application.properties` | Configuração do banco e do Spring Boot |
| `pom.xml` | Dependências e configuração do Maven |

Os pacotes Java ficam abaixo de `br.com.distribuidora`, para serem encontrados automaticamente pelo Spring Boot. A separação em pacotes mantém as mesmas rotas da API e os mesmos nomes de entidades e tabelas. Arquivos gerados na pasta `target/` não devem ser enviados ao GitHub.

Para baixar a organização no computador, com a aplicação parada e a branch `feat/estoque-reposicao` selecionada, execute `git pull`. Depois reinicie com `mvn clean spring-boot:run` para remover classes compiladas antigas. Acesse a porta definida no seu terminal, por exemplo `http://localhost:8081` quando `SERVER_PORT=8081`.

## Apagar produtos e limpar estoque

O botão **Apagar** de cada linha exclui o produto e seu histórico de movimentações após confirmação. **Limpar todo o estoque** apaga todos os produtos e seus históricos; exige a senha do administrador para confirmar usando a senha definida no servidor. Essas operações são permanentes e não significam apenas zerar os saldos.

API: `DELETE /api/produtos/{id}` apaga um produto (404 se inexistente); `DELETE /api/produtos` limpa o estoque, recebendo JSON com o campo `senha`; uma senha incorreta retorna HTTP 403. Configure outra senha pelo ambiente `ADMIN_DELETE_PASSWORD`. Sucesso retorna HTTP 204 sem conteúdo. O histórico é removido antes dos produtos, na mesma transação. Os testes de integração usam exclusivamente H2 em memória: `mvn clean test`.

## Interface

O painel utiliza preto, branco e tons de cinza, com cartões e campos arredondados. A navegação permite acessar visão geral, movimentações, produtos e reposição. A interface adapta os formulários a telas menores e mantém tabelas com rolagem horizontal quando necessário.

## Login

Ao abrir o painel, entre como **Administrador** com a senha definida no servidor. A autenticação por sessão protege a interface e a API; o botão **Sair** encerra a sessão. A senha é configurada pelo ambiente `ADMIN_DELETE_PASSWORD` e também é utilizada na confirmação de limpeza completa do estoque.

Requisições de alteração exigem um token CSRF. A interface o obtém automaticamente em `GET /csrf`. Para clientes de API, faça login com cookies de sessão e envie o token no cabeçalho indicado pela resposta de `/csrf`.

## Assistente de estoque com Gemini

A seção **Assistente de estoque** permite perguntar sobre saldos, entradas, saídas e reposição. O servidor consulta o PostgreSQL a cada pergunta e envia um resumo dos produtos e das movimentações diárias à API Gemini (`generateContent`). A chave permanece no servidor e é enviada pelo cabeçalho `x-goog-api-key`.

O modelo padrão é `gemini-3.5-flash-lite`, configurável por `GEMINI_MODEL`. Crie uma chave para o seu projeto no Google AI Studio: https://aistudio.google.com/apikey . Consulte a disponibilidade, os limites e os termos do plano da sua conta; não há garantia de acesso gratuito ilimitado. A faixa gratuita pode utilizar os dados enviados para melhorar os produtos da Google, conforme os termos do provedor.

Para ativar, pare o sistema, atualize a branch e configure no mesmo terminal PowerShell, mantendo também as variáveis do PostgreSQL e a porta:

```powershell
git pull
$env:GEMINI_API_KEY = 'SUA_CHAVE_DO_GEMINI'
mvn clean spring-boot:run
```

A variável `OPENAI_API_KEY` não é mais utilizada. Não grave chaves no código nem no GitHub. Sem a chave Gemini, o sistema continua funcionando e o assistente informa que a integração precisa ser ativada. Para outro modelo disponível na sua conta, configure `GEMINI_MODEL` antes de iniciar.

A consulta considera o fuso America/Sao_Paulo e períodos de 1 a 90 dias (a tela oferece 30/60/90). O contexto inclui saldos, histórico diário, primeira movimentação registrada e sugestões calculadas de 30 dias. Saídas podem representar vendas ou perdas; o contexto do assistente inclui preços de custo/venda informados, mas não inclui faturamento ou pedidos pendentes nesta etapa. Recomendações precisam ser revisadas pelo gestor.

A IA consulta informações e não executa SQL nem altera o banco. O histórico fica na memória da página, limitado às últimas oito mensagens por requisição; mudar o período inicia nova conversa. Nenhuma credencial de acesso ao sistema é enviada ao modelo. Há limite de 500 produtos e 240 mil caracteres de contexto, explicitado por erro em vez de truncamento silencioso.

Rotas protegidas: `GET /api/assistente/status` informa se há uma chave configurada (não comprova sua validade); `POST /api/assistente/perguntas` recebe `pergunta`, `periodoDias` e `historico` opcional, exigindo sessão e CSRF.

Falhas do Gemini são apresentadas sem expor o corpo bruto do erro ou a chave: limite diário/requisições (429), configuração/permissão (400/401/403) e modelo indisponível (404). Respostas incompletas, bloqueadas ou sem texto não são apresentadas como concluídas; partes de pensamento interno são descartadas.

Testes com H2 validam consulta ao contexto e acesso autenticado; testes HTTP simulam o Gemini sem chamadas reais. O modelo treinado de previsão de demanda ainda não está implementado.

## Preços de custo e venda

O cadastro e a tabela de estoque mostram custo e venda em reais por unidade de estoque (UN, CX ou PCT). Use **Preços** na linha do produto para editar os valores sem alterar saldo ou movimentações. Campos em branco significam “Não informado”; zero é um valor informado. Produtos anteriores continuam com os preços em branco até serem preenchidos.

Os preços são preenchidos manualmente. O custo é o último valor cadastrado, não uma média ponderada. A diferença entre preços não representa lucro líquido. O assistente Gemini permanece disponível para consultas em texto sobre o estoque.

## Saldo inicial, entregas e cálculo de venda

O novo produto aceita uma quantidade inicial não negativa. O cadastro e o registro de `SALDO_INICIAL` são salvos na mesma transação. Esse registro não é contado como recebimento ou venda nas consultas do assistente.

Selecione dias da semana de entrega no cadastro da empresa (1 = segunda até 7 = domingo). A ação **Entregas** na seção Fornecedores permite atualizar a agenda da empresa. Não se convertem prazos antigos em dias da semana: os produtos anteriores ficam sem agenda até ser informada. A cobertura desejada e o prazo numérico deixaram de ser campos de uso; as colunas antigas permanecem apenas para compatibilidade do banco existente.

A reposição considera a próxima entrega após hoje, no fuso America/Sao_Paulo, e o maior intervalo entre os dias cadastrados. Ponto = consumo médio × dias até próxima entrega + segurança; alvo = consumo médio × (dias até próxima entrega + maior intervalo entre entregas) + segurança, arredondando quantidades para cima. Não considera feriados nem horário de corte. Sem agenda, usa apenas a segurança, sem estimar um calendário; itens sem saída em 60 dias continuam bloqueados para recompra automática.

No cadastro e na edição de preços, escolha **Digitar meu preço** ou **Calcular por porcentagem**. O acréscimo é sobre o custo, não margem sobre a venda. Fórmula: custo × (1 + percentual / 100), arredondado ao múltiplo de R$ 0,50 mais próximo; empate arredonda para cima. Ex.: R$ 10 + 32,5% resulta em R$ 13,50. O servidor calcula com BigDecimal; o preço manual não é arredondado. Voltar ao modo manual permite qualquer preço com até duas casas decimais.

## Empresas fornecedoras

Cadastre os nomes manualmente na seção **Fornecedores** (ex.: Ambev, Coca-Cola). Nomes vazios ou repetidos, ignorando maiúsculas e espaços repetidos, são rejeitados. Nenhuma empresa é pré-cadastrada automaticamente.

Marque uma ou mais empresas no novo produto ou use **Fornecedores** na linha de um produto existente. A tabela mostra os fornecedores de cada produto; a seção de empresas mostra os produtos fornecidos por cada uma. O vínculo é muitos para muitos: cada produto pode ser entregue por várias empresas, e cada empresa pode fornecer vários produtos. Remover um vínculo não altera o saldo. Apagar produtos ou limpar o estoque preserva as empresas cadastradas.

Os dias de entrega são configurados por empresa. Cada produto usa a união dos dias dos fornecedores vinculados. Produtos anteriores ficam sem fornecedor até serem vinculados. Empresas anteriores ficam sem dias até você preencher sua agenda; dias antigos dos produtos não são copiados para empresas, pois podem ser ambíguos. O assistente recebe os nomes e dias dos fornecedores reais de cada produto e pode responder perguntas como “Quais produtos a Ambev entrega?”.

API autenticada: `GET/POST /api/fornecedores` e `PUT /api/produtos/{id}/fornecedores` com `fornecedorIds`. O cadastro de produto também aceita `fornecedorIds`. As rotas de escrita exigem CSRF; IDs inexistentes são rejeitados sem gravar cadastro ou saldo parcial.

A agenda da empresa é editada em `PUT /api/fornecedores/{id}/entregas`. A rota antiga de agenda por produto foi removida. Alterar os dias de uma empresa atualiza a agenda efetiva de todos os produtos vinculados sem alterar saldos.

## Navegação em páginas separadas

O menu lateral abre páginas independentes: `/index.html` (Início), `/produtos.html` (cadastro, listagem e edição), `/movimentacoes.html` (entradas/saídas), `/fornecedores.html` (empresas e entregas), `/reposicao.html` (sugestões) e `/assistente.html` (chat). A raiz `/` continua abrindo o Início depois do login. Cada página mostra apenas sua área e mantém o menu ativo. A navegação utiliza links normais, permitindo atualizar, abrir em outra aba e usar o histórico do navegador.

`app.js` contém sessão, CSRF e funções compartilhadas; cada página carrega seu próprio script. O assistente é inicializado somente em sua página; navegar para outra página encerra o histórico de conversa mantido em memória. Todas as páginas continuam protegidas pelo login. Não houve mudança nas tabelas ou nos dados.
