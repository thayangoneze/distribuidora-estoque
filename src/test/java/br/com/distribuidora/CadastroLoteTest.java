package br.com.distribuidora;
import br.com.distribuidora.dto.CadastroLote;
import br.com.distribuidora.model.*;
import br.com.distribuidora.repository.*;
import br.com.distribuidora.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:lote;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop","estoque.ia.api-key="})
@AutoConfigureMockMvc
@TestExecutionListeners(listeners=DependencyInjectionTestExecutionListener.class,mergeMode=TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
class CadastroLoteTest {
    @Autowired CadastroLoteService service;
    @Autowired EstoqueService estoque;
    @Autowired ProdutoRepository produtos;
    @Autowired FornecedorRepository fornecedores;
    @Autowired MovimentacaoRepository movimentos;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @BeforeEach void limpar(){movimentos.deleteAllInBatch();produtos.deleteAll();fornecedores.deleteAll();jdbc.update("delete from registro_cadastro_lote");}
    CadastroLote.Item item(String nome,String empresa) {
        return new CadastroLote.Item(nome,"UN",12,0,new BigDecimal("10"),null,new BigDecimal("32.5"),true,Set.of(empresa));
    }
    CadastroLote lote(CadastroLote.Item... itens){return new CadastroLote(UUID.randomUUID(),true,true,List.of(itens));}
    @Test void cadastraOs50ExemplosComSaldoPrecosEmpresasSemRepetir() throws Exception {
        CadastroLote.Item[] itens;
        try(var input=getClass().getResourceAsStream("/static/dados/produtos-exemplo.json")){itens=mapper.readValue(input,CadastroLote.Item[].class);}
        assertEquals(50,itens.length);var lote=lote(itens);var resultado=service.cadastrar(lote);
        assertEquals(50,resultado.produtosCriados());assertEquals(50,produtos.count());assertEquals(50,movimentos.count());assertEquals(9,fornecedores.count());
        Produto coca=produtos.findByNomeIgnoreCase("Coca-Cola original 2 L").get(0);assertEquals(48,coca.saldo);assertEquals(0,new BigDecimal("10.00").compareTo(coca.precoVenda));
        assertEquals(1,coca.fornecedores.size());assertEquals("Coca-Cola",coca.fornecedores.iterator().next().nome);
        assertTrue(movimentos.findAll().stream().allMatch(m->m.tipo==Movimentacao.Tipo.SALDO_INICIAL));
        assertEquals(resultado,service.cadastrar(lote));assertEquals(50,movimentos.count());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.cadastrar(lote(itens))).getStatusCode().value());assertEquals(50,produtos.count());
    }
    @Test void falhaNoSegundoItemReverteProdutosEmpresasEEntradas() {
        var primeiro=item("Primeiro","Nova empresa");
        var segundo=new CadastroLote.Item("Segundo","UN",12,0,null,null,new BigDecimal("30"),true,Set.of("Outra empresa"));
        assertThrows(ResponseStatusException.class,()->service.cadastrar(lote(primeiro,segundo)));
        assertEquals(0,produtos.count());assertEquals(0,fornecedores.count());assertEquals(0,movimentos.count());assertEquals(0,jdbc.queryForObject("select count(*) from registro_cadastro_lote",Integer.class));
    }
    @Test void rejeitaNomeDuplicadoEReutilizaFornecedorExistente() {
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.cadastrar(lote(item("Mesmo","Ambev"),item(" mesmo ","Ambev")))).getStatusCode().value());
        service.cadastrar(lote(item("Primeiro","Ambev")));service.cadastrar(lote(item("Segundo"," AMBEV ")));
        assertEquals(1,fornecedores.count());assertEquals(2,produtos.count());
        assertEquals(0,new BigDecimal("13.50").compareTo(produtos.findByNomeIgnoreCase("Primeiro").get(0).precoVenda));
    }
    @Test void exigeAutorizacaoParaCriarEmpresaEAceitaMultiplosFornecedores() {
        var dados=new CadastroLote(UUID.randomUUID(),true,false,List.of(item("Novo","Ambev")));
        assertThrows(ResponseStatusException.class,()->service.cadastrar(dados));assertEquals(0,produtos.count());
        var multi=new CadastroLote.Item("Água","UN",5,0,BigDecimal.ONE,new BigDecimal("2"),null,false,Set.of("Ambev","Coca-Cola"));
        service.cadastrar(lote(multi));assertEquals(2,produtos.findAll().get(0).fornecedores.size());
    }
    @Test void endpointExigeLoginCsrfEConfirmacao() throws Exception {
        String body=mapper.writeValueAsString(lote(item("Novo","Ambev")));
        mvc.perform(post("/api/produtos/lote").with(csrf()).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/produtos/lote").with(user("Administrador").roles("ADMIN")).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/produtos/lote").with(user("Administrador").roles("ADMIN")).with(csrf()).contentType("application/json").content(body.replace("\"revisado\":true","\"revisado\":false"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/produtos/lote").with(user("Administrador").roles("ADMIN")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk());assertEquals(1,produtos.count());
    }
}
