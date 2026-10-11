package br.com.distribuidora.repository;

import br.com.distribuidora.model.Movimentacao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

public interface MovimentacaoRepository extends JpaRepository<Movimentacao, Long> {
    @Modifying
    @Query("delete from Movimentacao m where m.produto.id in :ids")
    void apagarPorProdutos(@Param("ids") List<Long> ids);
    List<Movimentacao> findByCriadaEmBetweenOrderByCriadaEmAsc(Instant inicio, Instant fim);
    @Query("select min(m.criadaEm) from Movimentacao m")
    Instant primeiraMovimentacao();
    List<Movimentacao> findByTipoAndCriadaEmGreaterThanEqual(Movimentacao.Tipo tipo, Instant inicio);
}
