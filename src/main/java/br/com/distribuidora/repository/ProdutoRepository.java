package br.com.distribuidora.repository;

import br.com.distribuidora.model.Produto;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {
    boolean existsByCodigoBarrasAndIdNot(String codigo, Long id);
    boolean existsByCodigoBarras(String codigo);
    List<Produto> findByNomeIgnoreCase(String nome);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Produto p order by p.id")
    List<Produto> buscarTodosParaAtualizar();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Produto p where p.id = :id")
    Optional<Produto> buscarParaAtualizar(@Param("id") Long id);
}
