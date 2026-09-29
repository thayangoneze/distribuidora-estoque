# Distribuidora de bebidas — controle de estoque

Primeira etapa do sistema da Distribuidora de Bebidas do Gaúcho. API em Java 17, Spring Boot e PostgreSQL para cadastro, entradas e saídas, consulta de saldo e sugestão de reposição.

## Executar

1. Instale Java 17, Maven e PostgreSQL. Crie o banco `distribuidora`.
2. Configure `DB_URL`, `DB_USER` e `DB_PASSWORD` se não usar os padrões de desenvolvimento definidos em `application.properties`.
3. Execute `mvn spring-boot:run` na raiz do projeto. A API estará em `http://localhost:8080`.

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

- Implementados: cadastro básico, consulta de saldos, entradas e saídas transacionais, cálculo de demanda e sugestão com bloqueio para itens sem saída há 60 dias.
- Pendentes: interface web, autenticação e perfis, ajuste/confirmação de ordem de compra, portal atacadista, reserva e agendamento. A API atual é apenas para desenvolvimento local e não deve ser exposta publicamente antes de implementar autenticação.
- A criação automática das tabelas (`ddl-auto=update`) é provisória; antes de produção, substituir por migrações versionadas.

Consulte a documentação do projeto para os requisitos RF-01 a RF-07, regras RN-01 a RN-03 e casos de uso UC-01 a UC-10.
