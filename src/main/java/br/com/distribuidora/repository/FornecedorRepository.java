package br.com.distribuidora.repository;
import br.com.distribuidora.model.Fornecedor;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface FornecedorRepository extends JpaRepository<Fornecedor,Long> {
    boolean existsByNomeNormalizado(String nome);
    List<Fornecedor> findAllByOrderByNomeAsc();
}
