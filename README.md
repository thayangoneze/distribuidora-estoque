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
- Pendentes: autenticação e perfis, ajuste/confirmação de ordem de compra, portal atacadista, reserva e agendamento. O painel e a API atuais são apenas para desenvolvimento local e não devem ser expostos publicamente antes de implementar autenticação.
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

Para baixar a organização no computador, com a aplicação parada e a branch `feat/estoque-reposicao` selecionada, execute `git pull`. Depois reinicie com `mvn spring-boot:run`. Acesse a porta definida no seu terminal, por exemplo `http://localhost:8081` quando `SERVER_PORT=8081`.
