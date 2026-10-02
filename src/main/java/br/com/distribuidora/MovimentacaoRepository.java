package br.com.distribuidora;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;

public interface MovimentacaoRepository extends JpaRepository<Movimentacao, Long> {
    List<Movimentacao> findByTipoAndCriadaEmGreaterThanEqual(Movimentacao.Tipo tipo, Instant inicio);
}
