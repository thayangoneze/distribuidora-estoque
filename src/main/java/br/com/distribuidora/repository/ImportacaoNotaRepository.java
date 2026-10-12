package br.com.distribuidora.repository;

import br.com.distribuidora.model.ImportacaoNota;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface ImportacaoNotaRepository extends JpaRepository<ImportacaoNota,UUID> {
    Optional<ImportacaoNota> findByHashArquivo(String hash);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from ImportacaoNota n where n.id=:id")
    Optional<ImportacaoNota> buscarParaConfirmar(@Param("id") UUID id);
}
