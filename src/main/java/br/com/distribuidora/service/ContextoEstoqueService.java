package br.com.distribuidora.service;

import br.com.distribuidora.repository.*;
import br.com.distribuidora.model.Movimentacao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.*;

@Service
public class ContextoEstoqueService {
    private final ProdutoRepository produtos;
    private final MovimentacaoRepository movimentacoes;
    private final EstoqueService estoque;
    public ContextoEstoqueService(ProdutoRepository produtos, MovimentacaoRepository movimentacoes, EstoqueService estoque) {
        this.produtos = produtos; this.movimentacoes = movimentacoes; this.estoque = estoque;
    }
    public record Dia(String data, long entradas, long saidas) {}
    public record Item(Long id, String nome, int saldo, int estoqueSeguranca, int prazoEntregaDias,
                       int coberturaDesejadaDias, List<Dia> movimentacoesDiarias) {}
    public record Contexto(String atualizadoEm, String inicioPeriodo, int periodoDias,
                           Instant primeiraMovimentacaoRegistrada, List<Item> produtos,
                           List<br.com.distribuidora.dto.Sugestao> sugestoesBase30Dias) {}
    @Transactional(readOnly = true)
    public Contexto consultar(int dias) {
        Instant agora = Instant.now();
        ZoneId zona = ZoneId.of("America/Sao_Paulo");
        LocalDate primeiroDia = agora.atZone(zona).toLocalDate().minusDays(dias - 1);
        Instant inicio = primeiroDia.atStartOfDay(zona).toInstant();
        var lista = produtos.findAll();
        if (lista.size() > 500) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
            "O catálogo excede o limite desta versão do assistente (500 produtos).");
        Map<Long, Map<LocalDate, long[]>> totais = new HashMap<>();
        for (Movimentacao m : movimentacoes.findByCriadaEmBetweenOrderByCriadaEmAsc(inicio, agora)) {
            LocalDate dia = m.criadaEm.atZone(zona).toLocalDate();
            long[] t = totais.computeIfAbsent(m.produto.id, id -> new TreeMap<>()).computeIfAbsent(dia, d -> new long[2]);
            t[m.tipo == Movimentacao.Tipo.ENTRADA ? 0 : 1] += m.quantidade;
        }
        List<Item> itens = lista.stream().sorted(Comparator.comparing(p -> p.id)).map(p -> {
            var diarios = totais.getOrDefault(p.id, Map.of()).entrySet().stream()
                .map(e -> new Dia(e.getKey().toString(), e.getValue()[0], e.getValue()[1])).toList();
            return new Item(p.id, p.nome, p.saldo, p.estoqueSeguranca, p.prazoEntregaDias, p.coberturaDesejadaDias, diarios);
        }).toList();
        return new Contexto(agora.toString(), primeiroDia.toString(), dias,
            movimentacoes.primeiraMovimentacao(), itens, estoque.sugestoes(30));
    }
}
