package br.com.distribuidora.repository;
import br.com.distribuidora.model.Venda;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface VendaRepository extends JpaRepository<Venda,UUID> {
    List<Venda> findTop20ByOrderByCriadaEmDesc();
}
