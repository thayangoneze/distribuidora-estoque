package br.com.distribuidora.service;
import br.com.distribuidora.dto.CadastroLote;
import br.com.distribuidora.model.*;
import br.com.distribuidora.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.time.Instant;
import java.security.MessageDigest;
@Service
public class CadastroLoteService {
    private final ProdutoRepository produtos;
    private final FornecedorRepository fornecedores;
    private final FornecedorService cadastroFornecedor;
    private final EstoqueService estoque;
    private final EntityManager em;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final TransactionTemplate transacao;
    public CadastroLoteService(ProdutoRepository produtos,FornecedorRepository fornecedores,FornecedorService cadastroFornecedor,
        EstoqueService estoque,EntityManager em,ObjectMapper mapper,Validator validator,PlatformTransactionManager tx) {
        this.produtos=produtos;this.fornecedores=fornecedores;this.cadastroFornecedor=cadastroFornecedor;this.estoque=estoque;
        this.em=em;this.mapper=mapper;this.validator=validator;this.transacao=new TransactionTemplate(tx);
    }
    public record Resultado(UUID id,int produtosCriados,int fornecedoresCriados) {}
    // Serializa importações nesta instância até o commit, e não apenas até o retorno do método transacional.
    public synchronized Resultado cadastrar(CadastroLote lote) {
        if(!validator.validate(lote).isEmpty())throw erro(HttpStatus.BAD_REQUEST,"Revise os dados do lote e marque a confirmação.");
        String assinatura;
        try{assinatura=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(lote)));}
        catch(Exception e){throw erro(HttpStatus.BAD_REQUEST,"Não foi possível validar o lote.");}
        return transacao.execute(status -> {
            RegistroCadastroLote salvo=em.find(RegistroCadastroLote.class,lote.id());
            if(salvo!=null) {
                if(!salvo.assinatura.equals(assinatura))throw erro(HttpStatus.CONFLICT,"Identificador já usado por outro lote.");
                return new Resultado(salvo.id,salvo.produtosCriados,salvo.fornecedoresCriados);
            }
            Set<String> nomes=new HashSet<>();
            produtos.findAll().forEach(p -> nomes.add(normalizar(p.nome)));
            for(var item:lote.itens()) if(!nomes.add(normalizar(item.nome())))
                throw erro(HttpStatus.CONFLICT,"Produto repetido ou já cadastrado: "+item.nome()+". Retire-o da lista antes de confirmar.");
            RegistroCadastroLote registro=new RegistroCadastroLote();registro.id=lote.id();registro.assinatura=assinatura;registro.criadoEm=Instant.now();
            em.persist(registro);em.flush();
            Map<String,Fornecedor> empresas=new HashMap<>();fornecedores.findAll().forEach(f -> empresas.put(f.nomeNormalizado,f));
            int novas=0;
            for(var item:lote.itens()) {
                Produto p=new Produto();p.nome=item.nome().strip().replaceAll("\\s+"," ");p.unidadeEstoque=item.unidadeEstoque();
                p.saldo=item.saldo();p.estoqueSeguranca=item.estoqueSeguranca();p.precoCusto=item.precoCusto();p.precoVenda=item.precoVenda();
                p.calcularPrecoVenda=item.calcularPrecoVenda();p.percentualAcrescimo=item.percentualAcrescimo();
                for(String nome:item.fornecedores()==null?Set.<String>of():item.fornecedores()) {
                    String chave=normalizar(nome);Fornecedor f=empresas.get(chave);
                    if(f==null) {
                        if(!lote.criarFornecedores())throw erro(HttpStatus.BAD_REQUEST,"Cadastre a empresa "+nome+" ou autorize sua criação no lote.");
                        f=cadastroFornecedor.criar(nome,Set.of());empresas.put(chave,f);novas++;
                    }
                    p.fornecedorIds.add(f.id);
                }
                estoque.criar(p);
            }
            registro.produtosCriados=lote.itens().size();registro.fornecedoresCriados=novas;em.flush();
            return new Resultado(registro.id,registro.produtosCriados,registro.fornecedoresCriados);
        });
    }
    private String normalizar(String nome){return nome.strip().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);}
    private static ResponseStatusException erro(HttpStatus status,String mensagem){return new ResponseStatusException(status,mensagem);}
}
