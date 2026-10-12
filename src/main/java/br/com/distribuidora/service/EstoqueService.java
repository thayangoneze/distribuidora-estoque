package br.com.distribuidora.service;

import br.com.distribuidora.dto.Sugestao;
import br.com.distribuidora.dto.PrecosProduto;
import br.com.distribuidora.model.Movimentacao;
import br.com.distribuidora.model.Produto;
import br.com.distribuidora.repository.MovimentacaoRepository;
import br.com.distribuidora.repository.ProdutoRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EstoqueService {
    private final ProdutoRepository produtos;
    private final MovimentacaoRepository movimentacoes;
    public EstoqueService(ProdutoRepository produtos, MovimentacaoRepository movimentacoes) {
        this.produtos = produtos; this.movimentacoes = movimentacoes;
    }
    public static BigDecimal calcularVenda(BigDecimal custo, BigDecimal percentual) {
        if (custo == null || percentual == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o custo e a porcentagem para calcular a venda.");
        BigDecimal valor = custo.multiply(BigDecimal.ONE.add(percentual.movePointLeft(2)));
        BigDecimal arredondado = valor.multiply(BigDecimal.valueOf(2)).setScale(0, RoundingMode.HALF_UP)
            .divide(BigDecimal.valueOf(2)).setScale(2);
        if (arredondado.compareTo(new BigDecimal("999999999999.99")) > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preço calculado excede o limite permitido.");
        return arredondado;
    }
    @Transactional
    public Produto criar(Produto p) {
        p.id = null; p.versao = null; p.prazoEntregaDias = 0; p.coberturaDesejadaDias = 0;
        if (p.diasEntrega == null) p.diasEntrega = new HashSet<>();
        if (p.calcularPrecoVenda) p.precoVenda = calcularVenda(p.precoCusto, p.percentualAcrescimo);
        else p.percentualAcrescimo = null;
        Produto salvo = produtos.save(p);
        if (p.saldo > 0) movimentacoes.save(new Movimentacao(salvo, Movimentacao.Tipo.SALDO_INICIAL, p.saldo));
        return salvo;
    }
    @Transactional
    public Produto atualizarEntregas(Long id, Set<Integer> dias) {
        Produto p = produtos.buscarParaAtualizar(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado"));
        p.diasEntrega = new HashSet<>(dias);
        return produtos.save(p);
    }
    // Próxima entrega após hoje; não presume que ainda é possível receber no mesmo dia.
    public static int diasAteEntrega(Set<Integer> dias, LocalDate hoje) {
        return java.util.stream.IntStream.rangeClosed(1, 7)
            .filter(n -> dias.contains(hoje.plusDays(n).getDayOfWeek().getValue())).findFirst().orElse(0);
    }
    public static int maiorIntervaloEntrega(Set<Integer> dias) {
        return dias.stream().mapToInt(d -> java.util.stream.IntStream.rangeClosed(1, 7)
            .filter(n -> dias.contains((d - 1 + n) % 7 + 1)).findFirst().orElse(0)).max().orElse(0);
    }
    @Transactional
    public Produto atualizarPrecos(Long id, PrecosProduto dados) {
        Produto produto = produtos.buscarParaAtualizar(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado"));
        produto.precoCusto = dados.precoCusto();
        produto.precoVenda = dados.calcularPrecoVenda() ? calcularVenda(dados.precoCusto(), dados.percentualAcrescimo()) : dados.precoVenda();
        produto.percentualAcrescimo = dados.calcularPrecoVenda() ? dados.percentualAcrescimo() : null;
        return produtos.save(produto);
    }
    @Transactional
    public void apagarProduto(Long id) {
        Produto produto = produtos.buscarParaAtualizar(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado"));
        movimentacoes.apagarPorProdutos(List.of(id));
        produtos.delete(produto);
    }
    @Transactional
    public void limparEstoque() {
        List<Produto> lista = produtos.buscarTodosParaAtualizar();
        List<Long> ids = lista.stream().map(p -> p.id).toList();
        if (ids.isEmpty()) return;
        movimentacoes.apagarPorProdutos(ids);
        produtos.deleteAll(lista);
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
            Set<Integer> agenda = p.diasEntrega == null ? Set.of() : p.diasEntrega;
            int espera = diasAteEntrega(agenda, agora.atZone(ZoneId.of("America/Sao_Paulo")).toLocalDate());
            int intervalo = maiorIntervaloEntrega(agenda);
            int ponto = (int) Math.ceil(media * espera) + p.estoqueSeguranca;
            boolean parado = !ativos60.contains(p.id);
            int alvo = (int) Math.ceil(media * (espera + intervalo)) + p.estoqueSeguranca;
            int quantidade = parado || p.saldo > ponto ? 0 : Math.max(0, alvo - p.saldo);
            return new Sugestao(p.id, p.nome, p.saldo, p.estoqueSeguranca, media, ponto, quantidade,
                parado, p.saldo <= p.estoqueSeguranca);
        }).sorted(Comparator.comparingInt((Sugestao s) -> s.quantidadeSugerida()).reversed()).toList();
    }
}
