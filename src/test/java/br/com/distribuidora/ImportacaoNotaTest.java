package br.com.distribuidora;

import br.com.distribuidora.dto.*;
import br.com.distribuidora.model.*;
import br.com.distribuidora.repository.*;
import br.com.distribuidora.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.*;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:notas;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
    "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop", "estoque.ia.api-key="})
@Import(ImportacaoNotaTest.Config.class)
@AutoConfigureMockMvc
@TestExecutionListeners(listeners=DependencyInjectionTestExecutionListener.class,
    mergeMode=TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
class ImportacaoNotaTest {
    static final AtomicInteger chamadas=new AtomicInteger();
    @TestConfiguration static class Config {
        @Bean @Primary GeminiEstoqueClient notaTesteClient() {
            return new GeminiEstoqueClient(RestClient.create(),new ObjectMapper(),"","") {
                @Override public NotaExtraida extrairNota(byte[] bytes,String mime) {
                    assertEquals("image/png",mime); chamadas.incrementAndGet();
                    return new NotaExtraida("Fornecedor", "10", "2026-10-12",
                        List.of(new NotaExtraida.Item("Refrigerante 2 L",new BigDecimal("10"),"CX",new BigDecimal("50.25"),"Conferir embalagem")),null);
                }
            };
        }
    }
    @Autowired ImportacaoNotaService service;
    @Autowired ProdutoRepository produtos;
    @Autowired MovimentacaoRepository movimentos;
    @Autowired ImportacaoNotaRepository notas;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @BeforeEach void limpar() {
        movimentos.deleteAllInBatch(); produtos.deleteAllInBatch(); notas.deleteAllInBatch(); chamadas.set(0);
    }
    MockMultipartFile arquivo() {
        return new MockMultipartFile("arquivo","nota.png","image/png",new byte[]{(byte)0x89,'P','N','G',13,10,26,10,0});
    }
    ConfirmacaoNota.Item item(Long id,String nome,String unidade,int quantidade) {
        return new ConfirmacaoNota.Item(id,nome,unidade,quantidade,new BigDecimal("10"),"CX",new BigDecimal("50.25"),10,3,7,new BigDecimal("8.00"));
    }
    ConfirmacaoNota confirmacao(ConfirmacaoNota.Item... itens) {
        return new ConfirmacaoNota("Fornecedor","10","2026-10-12",true,List.of(itens));
    }
    @Test void analisarCriaRevisaoSemAlterarEstoqueEReutilizaMesmoArquivo() {
        var r=service.analisar(arquivo());
        assertEquals(1,notas.count()); assertEquals(0,produtos.count()); assertEquals(0,movimentos.count());
        assertEquals(r.importacaoId(),service.analisar(arquivo()).importacaoId());
        assertEquals(1,chamadas.get());
    }
    @Test void confirmarNovoProdutoRegistraQuantidadeRevisadaEPrecoOriginal() {
        var r=service.analisar(arquivo());
        service.confirmar(r.importacaoId(),confirmacao(item(null,"Refrigerante 2 L","UN",120)));
        var p=produtos.findAll().get(0); assertEquals(120,p.saldo); assertEquals("UN",p.unidadeEstoque);
        assertEquals(0,new BigDecimal("4.19").compareTo(p.precoCusto));
        assertEquals(0,new BigDecimal("8.00").compareTo(p.precoVenda));
        var m=movimentos.findAll().get(0); assertEquals(120,m.quantidade);
        assertEquals(0,new BigDecimal("50.25").compareTo(m.valorUnitarioNota));
        assertEquals("CX",m.unidadeNota); assertEquals(0,new BigDecimal("10").compareTo(m.quantidadeNota));
        assertNotNull(notas.findById(r.importacaoId()).orElseThrow().confirmadaEm);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.confirmar(r.importacaoId(),confirmacao(item(null,"Outro","UN",5)))).getStatusCode().value());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.analisar(arquivo())).getStatusCode().value());
        assertEquals(1,movimentos.count());
    }
    @Test void produtoExistenteRecebeEntradaSemNovoCadastro() {
        Produto p=new Produto(); p.nome="Refrigerante 2 L"; p.saldo=8; p.unidadeEstoque="CX"; p.precoVenda=new BigDecimal("70.00"); p=produtos.save(p);
        var r=service.analisar(arquivo());service.confirmar(r.importacaoId(),confirmacao(item(p.id,p.nome,"CX",10)));
        assertEquals(1,produtos.count()); assertEquals(18,produtos.findById(p.id).orElseThrow().saldo);
        assertEquals(0,new BigDecimal("70.00").compareTo(produtos.findById(p.id).orElseThrow().precoVenda));
        assertEquals(0,new BigDecimal("50.25").compareTo(produtos.findById(p.id).orElseThrow().precoCusto));
    }
    @Test void notaSemPrecoPreservaCustoExistente() {
        Produto p=new Produto(); p.nome="Já cadastrado"; p.precoCusto=new BigDecimal("6.50"); p=produtos.save(p);
        var r=service.analisar(arquivo());
        var item=new ConfirmacaoNota.Item(p.id,p.nome,"UN",12,null,null,null,0,0,0,null);
        service.confirmar(r.importacaoId(),confirmacao(item));
        assertEquals(0,new BigDecimal("6.50").compareTo(produtos.findById(p.id).orElseThrow().precoCusto));
        assertNull(produtos.findById(p.id).orElseThrow().precoVenda);
    }
    @Test void falhaEmItemPosteriorReverteTudoIncluindoProdutoNovo() {
        var r=service.analisar(arquivo());
        assertThrows(ResponseStatusException.class,()->service.confirmar(r.importacaoId(),confirmacao(
            item(null,"Duplicado","UN",5),item(null,"Duplicado","CX",10))));
        assertEquals(0,produtos.count()); assertEquals(0,movimentos.count());
        assertNull(notas.findById(r.importacaoId()).orElseThrow().confirmadaEm);
    }
    @Test void semRevisaoOuQuantidadeInvalidaNaoAlteraEstoque() {
        var r=service.analisar(arquivo());
        var dados=new ConfirmacaoNota(null,null,null,false,List.of(item(null,"Novo","UN",5)));
        assertThrows(ResponseStatusException.class,()->service.confirmar(r.importacaoId(),dados));
        assertThrows(ResponseStatusException.class,()->service.confirmar(r.importacaoId(),confirmacao(item(null,"Novo","UN",0))));
        assertEquals(0,produtos.count()); assertEquals(0,movimentos.count());
    }
    @Test void rejeitaTextoDisfarcadoDeImagemAntesDaIA() {
        var falso=new MockMultipartFile("arquivo","nota.png","image/png","nao e imagem".getBytes());
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service.analisar(falso)).getStatusCode().value());
        assertEquals(0,chamadas.get()); assertEquals(0,notas.count());
    }
    @Test void apiExigeLoginCsrfEConfirmacaoRevisada() throws Exception {
        mvc.perform(multipart("/api/notas/analisar").file(arquivo()).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/notas/analisar").file(arquivo()).with(user("Administrador").roles("ADMIN")))
            .andExpect(status().isForbidden());
        var resultado=mvc.perform(multipart("/api/notas/analisar").file(arquivo())
            .with(user("Administrador").roles("ADMIN")).with(csrf())).andExpect(status().isOk()).andReturn();
        UUID id=UUID.fromString(mapper.readTree(resultado.getResponse().getContentAsString()).path("importacaoId").asText());
        var dados=confirmacao(item(null,"Refrigerante 2 L","UN",120));
        mvc.perform(post("/api/notas/"+id+"/confirmar").with(user("Administrador").roles("ADMIN")).with(csrf())
            .contentType("application/json").content(mapper.writeValueAsString(new ConfirmacaoNota(null,null,null,false,dados.itens()))))
            .andExpect(status().isBadRequest());
        assertEquals(0,produtos.count());
        mvc.perform(post("/api/notas/"+id+"/confirmar").with(user("Administrador").roles("ADMIN")).with(csrf())
            .contentType("application/json").content(mapper.writeValueAsString(dados))).andExpect(status().isOk());
        assertEquals(120,produtos.findAll().get(0).saldo);
    }
}
