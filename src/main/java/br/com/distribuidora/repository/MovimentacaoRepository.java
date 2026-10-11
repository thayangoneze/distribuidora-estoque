package br.com.distribuidora.repository;

import br.com.distribuidora.model.Movimentacao;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;

public interface MovimentacaoRepository extends JpaRepository<Movimentacao, Long> {
    List<Movimentacao> findByTipoAndCriadaEmGreaterThanEqual(Movimentacao.Tipo tipo, Instant inicio);
}
