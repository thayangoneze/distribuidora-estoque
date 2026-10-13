package br.com.distribuidora.service;
import br.com.distribuidora.model.Fornecedor;
import br.com.distribuidora.repository.FornecedorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
@Service
public class FornecedorService {
    private final FornecedorRepository fornecedores;
    public FornecedorService(FornecedorRepository fornecedores) { this.fornecedores=fornecedores; }
    @Transactional
    public Fornecedor criar(String nome, Set<Integer> dias) {
        String limpo=nome.strip().replaceAll("\\s+"," ");
        String normalizado=limpo.toLowerCase(Locale.ROOT);
        if(fornecedores.existsByNomeNormalizado(normalizado))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Esta empresa já está cadastrada.");
        Fornecedor f=new Fornecedor(); f.nome=limpo; f.nomeNormalizado=normalizado;
        f.diasEntrega=dias==null ? new HashSet<>() : new HashSet<>(dias);
        try { return fornecedores.saveAndFlush(f); }
        catch(DataIntegrityViolationException e) { throw new ResponseStatusException(HttpStatus.CONFLICT,"Esta empresa já está cadastrada."); }
    }
    @Transactional
    public Fornecedor atualizarEntregas(Long id, Set<Integer> dias) {
        Fornecedor f=fornecedores.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Empresa não encontrada."));
        f.diasEntrega=new HashSet<>(dias);
        return fornecedores.save(f);
    }
    @Transactional(readOnly=true)
    public List<Fornecedor> listar() { return fornecedores.findAllByOrderByNomeAsc(); }
    @Transactional(readOnly=true)
    public Set<Fornecedor> resolver(Set<Long> ids) {
        if(ids==null || ids.isEmpty()) return new HashSet<>();
        List<Fornecedor> lista=fornecedores.findAllById(ids);
        if(lista.size()!=ids.size()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Uma empresa selecionada não foi encontrada. Atualize a lista.");
        return new HashSet<>(lista);
    }
}
