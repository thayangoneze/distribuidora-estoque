package br.com.distribuidora;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EstoqueService {
    private final ProdutoRepository produtos;
    private final MovimentacaoRepository movimentacoes;
    public EstoqueService(ProdutoRepository produtos, MovimentacaoRepository movimentacoes) {
        this.produtos = produtos; this.movimentacoes = movimentacoes;
    }
    @Transactional
    public Produto movimentar(Long id, Movimentacao.Tipo tipo, int quantidade) {
        if (quantidade <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantidade deve ser positiva");
        Produto produto = produtos.buscarParaAtualizar(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado"));
        if (tipo == Movimentacao.Tipo.SAIDA && produto.saldo < quantidade)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Saldo insuficiente");
        if (tipo == Movimentacao.Tipo.ENTRADA && (long) produto.saldo + quantidade > Integer.MAX_VALUE)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saldo excede limite permitido");
        produto.saldo += tipo == Movimentacao.Tipo.ENTRADA ? quantidade : -quantidade;
        movimentacoes.save(new Movimentacao(produto, tipo, quantidade));
        return produtos.save(produto);
    }
    public record Sugestao(Long produtoId, String nome, int saldo, int estoqueSeguranca,
                           double mediaDiaria, int pontoReposicao, int quantidadeSugerida, boolean baixoGiro) {}
    @Transactional(readOnly = true)
    public List<Sugestao> sugestoes(int dias) {
        if (dias != 30 && dias != 60 && dias != 90)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Período deve ser 30, 60 ou 90 dias");
        Instant agora = Instant.now();
        Map<Long, Integer> saidas = movimentacoes.findByTipoAndCriadaEmGreaterThanEqual(Movimentacao.Tipo.SAIDA, agora.minus(Duration.ofDays(dias)))
            .stream().collect(Collectors.groupingBy(m -> m.produto.id, Collectors.summingInt(m -> m.quantidade)));
        Set<Long> ativos60 = movimentacoes.findByTipoAndCriadaEmGreaterThanEqual(Movimentacao.Tipo.SAIDA, agora.minus(Duration.ofDays(60)))
            .stream().map(m -> m.produto.id).collect(Collectors.toSet());
        return produtos.findAll().stream().map(p -> {
            double media = saidas.getOrDefault(p.id, 0) / (double) dias;
            int ponto = (int) Math.ceil(media * p.prazoEntregaDias) + p.estoqueSeguranca;
            boolean parado = !ativos60.contains(p.id);
            int alvo = (int) Math.ceil(media * (p.prazoEntregaDias + p.coberturaDesejadaDias)) + p.estoqueSeguranca;
            int quantidade = parado || p.saldo > ponto ? 0 : Math.max(0, alvo - p.saldo);
            return new Sugestao(p.id, p.nome, p.saldo, p.estoqueSeguranca, media, ponto, quantidade, parado);
        }).sorted(Comparator.comparingInt((Sugestao s) -> s.quantidadeSugerida()).reversed()).toList();
    }
}
