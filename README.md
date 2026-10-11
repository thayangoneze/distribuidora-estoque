# Distribuidora de bebidas — controle de estoque

Primeira etapa do sistema da Distribuidora de Bebidas do Gaúcho. Painel web e API em Java 17, Spring Boot e PostgreSQL para cadastro, entradas e saídas, consulta de saldo e sugestão de reposição.

## Executar

1. Instale Java 17, Maven e PostgreSQL. Crie o banco `distribuidora`.
2. Configure `DB_URL`, `DB_USER` e `DB_PASSWORD` se não usar os padrões de desenvolvimento definidos em `application.properties`.
3. Execute `mvn spring-boot:run` na raiz do projeto. Abra `http://localhost:8080` para usar o painel. A API estará em `http://localhost:8080/api`.

Exemplo de uso:

```bash
curl -X POST http://localhost:8080/api/produtos -H 'Content-Type: application/json' -d '{"nome":"Refrigerante 2 L","estoqueSeguranca":10,"prazoEntregaDias":3,"coberturaDesejadaDias":7}'
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

O botão **Apagar** de cada linha exclui o produto e seu histórico de movimentações após confirmação. **Limpar todo o estoque** apaga todos os produtos e seus históricos; exige a senha do administrador para confirmar (provisoriamente `123456`). Essas operações são permanentes e não significam apenas zerar os saldos.

API: `DELETE /api/produtos/{id}` apaga um produto (404 se inexistente); `DELETE /api/produtos` limpa o estoque, recebendo JSON com o campo `senha`; uma senha incorreta retorna HTTP 403. Configure outra senha pelo ambiente `ADMIN_DELETE_PASSWORD`. Sucesso retorna HTTP 204 sem conteúdo. O histórico é removido antes dos produtos, na mesma transação. Os testes de integração usam exclusivamente H2 em memória: `mvn clean test`.

## Interface

O painel utiliza preto, branco e tons de cinza, com cartões e campos arredondados. A navegação permite acessar visão geral, movimentações, produtos e reposição. A interface adapta os formulários a telas menores e mantém tabelas com rolagem horizontal quando necessário.

## Login

Ao abrir o painel, entre como **Administrador** com a senha provisória **123456**. A autenticação por sessão protege a interface e a API; o botão **Sair** encerra a sessão. A senha é configurada pelo ambiente `ADMIN_DELETE_PASSWORD` e também é utilizada na confirmação de limpeza completa do estoque.

Requisições de alteração exigem um token CSRF. A interface o obtém automaticamente em `GET /csrf`. Para clientes de API, faça login com cookies de sessão e envie o token no cabeçalho indicado pela resposta de `/csrf`.

## Assistente de estoque com IA

A seção **Assistente de estoque** permite perguntar sobre saldos, entradas, saídas e reposição. O servidor consulta o PostgreSQL a cada pergunta e envia um resumo dos produtos e das movimentações diárias do período à API Responses da OpenAI. A chave permanece no servidor. O modelo padrão é `gpt-4.1-mini`, configurável por `OPENAI_MODEL`.

Para ativar, pare o sistema e configure no mesmo terminal PowerShell, além da conexão com o banco e da porta:

```powershell
$env:OPENAI_API_KEY = 'SUA_CHAVE_DA_API'
mvn clean spring-boot:run
```

Não grave a chave no código ou no GitHub. Sem uma chave, o sistema continua funcionando e informa que a integração de IA precisa ser ativada. A integração requer acesso à API OpenAI; uma sessão no ChatGPT não configura automaticamente essa chave. O uso do provedor é cobrado conforme sua conta de API.

A consulta considera datas no fuso America/Sao_Paulo e aceita períodos de 1 a 90 dias (a tela oferece 30/60/90). O contexto inclui saldos, histórico diário, primeira movimentação registrada e as sugestões calculadas de 30 dias. Saídas podem representar vendas ou perdas; não há dados de faturamento, preços ou pedidos pendentes nesta etapa. A IA deve explicar essa limitação e não inventar números. As recomendações devem ser revisadas pelo gestor.

Esta etapa é de consulta: a IA não executa SQL e não altera o banco. O histórico da conversa fica apenas na memória da página, limitado às últimas oito mensagens por requisição; mudar o período inicia nova conversa. Os dados enviados são somente do estoque, sem credenciais de acesso. O limite inicial é 500 produtos e 240 mil caracteres de contexto; limites excedidos geram uma mensagem, sem truncar silenciosamente o catálogo. As chamadas usam `store=false`.

Rotas protegidas: `GET /api/assistente/status` informa se a chave foi configurada (não comprova que ela é válida); `POST /api/assistente/perguntas` recebe `pergunta`, `periodoDias` e `historico` opcional, exigindo sessão e CSRF.

Testes de integração com H2 validam a leitura do contexto sem alterar registros, acesso autenticado e validação. Testes HTTP simulam respostas e erros do provedor, sem chamadas pagas. O teste ao vivo requer configurar uma chave real. Leitura de notas por imagem e modelo treinado de previsão de demanda ainda não estão implementados.
