package br.com.distribuidora;

import br.com.distribuidora.model.Produto;
import br.com.distribuidora.model.Movimentacao;
import br.com.distribuidora.repository.ProdutoRepository;
import br.com.distribuidora.repository.MovimentacaoRepository;
import br.com.distribuidora.service.EstoqueService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import org.springframework.mock.web.MockHttpSession;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:exclusao;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "estoque.senha-administrador=123456", "estoque.ia.api-key="
})
@AutoConfigureMockMvc
@Transactional
@TestExecutionListeners(listeners = {DependencyInjectionTestExecutionListener.class,
    TransactionalTestExecutionListener.class}, mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
class ExclusaoEstoqueTest {
    @Autowired ProdutoRepository produtos;
    @Autowired MovimentacaoRepository movimentacoes;
    @Autowired EstoqueService estoque;
    @Autowired MockMvc mvc;
    @Autowired br.com.distribuidora.service.ContextoEstoqueService contextoIa;

    private Produto criar(String nome) {
        Produto p = new Produto(); p.nome = nome;
        p = produtos.saveAndFlush(p);
        estoque.movimentar(p.id, Movimentacao.Tipo.ENTRADA, 40);
        estoque.movimentar(p.id, Movimentacao.Tipo.SAIDA, 8);
        return p;
    }

    @Test void cadastrarEEditarPrecosPreservaSaldoEHistorico() throws Exception {
        var resposta=mvc.perform(post("/api/produtos").with(user("Administrador").roles("ADMIN")).with(csrf())
            .contentType("application/json").content("{\"nome\":\"Com preços\",\"precoCusto\":4.19,\"precoVenda\":8.00}"))
            .andExpect(status().isCreated()).andReturn();
        Long id=new com.fasterxml.jackson.databind.ObjectMapper().readTree(resposta.getResponse().getContentAsString()).get("id").asLong();
        assertEquals(0,new java.math.BigDecimal("4.19").compareTo(produtos.findById(id).orElseThrow().precoCusto));
        estoque.movimentar(id,Movimentacao.Tipo.ENTRADA,12);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/produtos/"+id+"/precos")
            .with(user("Administrador").roles("ADMIN")).with(csrf()).contentType("application/json")
            .content("{\"precoCusto\":5.50,\"precoVenda\":9.90}")).andExpect(status().isOk());
        var p=produtos.findById(id).orElseThrow();
        assertEquals(12,p.saldo); assertEquals(1,movimentacoes.count());
        assertEquals(0,new java.math.BigDecimal("9.90").compareTo(p.precoVenda));
        assertEquals(0,new java.math.BigDecimal("5.50").compareTo(contextoIa.consultar(30).produtos().get(0).precoCusto()));
    }
    @Test void precosInvalidosSaoRejeitadosESemPrecoContinuaDesconhecido() throws Exception {
        Produto p=criar("Sem preço"); assertNull(p.precoCusto); assertNull(p.precoVenda);
        for(String valor:java.util.List.of("-1","1.234","1000000000000")) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/produtos/"+p.id+"/precos")
                .with(user("Administrador").roles("ADMIN")).with(csrf()).contentType("application/json")
                .content("{\"precoCusto\":"+valor+",\"precoVenda\":8}")).andExpect(status().isBadRequest());
        }
        assertNull(produtos.findById(p.id).orElseThrow().precoCusto);
    }

    @Test void apagarUmProdutoRemoveSeuHistoricoEPreservaOutro() throws Exception {
        Produto primeiro = criar("Primeiro");
        Produto outro = criar("Outro");
        mvc.perform(delete("/api/produtos/" + primeiro.id).with(user("Administrador").roles("ADMIN")).with(csrf())).andExpect(status().isNoContent());
        produtos.flush();
        assertFalse(produtos.existsById(primeiro.id));
        assertTrue(produtos.existsById(outro.id));
        assertEquals(2, movimentacoes.count());
        assertTrue(movimentacoes.findAll().stream().allMatch(m -> m.produto.id.equals(outro.id)));
    }

    @Test void limparRemoveProdutosEHistoricosEPodeSerRepetido() throws Exception {
        criar("Primeiro"); criar("Outro");
        mvc.perform(delete("/api/produtos").with(user("Administrador").roles("ADMIN")).with(csrf()).contentType("application/json").content("{\"senha\":\"123456\"}")).andExpect(status().isNoContent());
        assertEquals(0, produtos.count()); assertEquals(0, movimentacoes.count());
        assertTrue(estoque.sugestoes(30).isEmpty());
        mvc.perform(delete("/api/produtos").with(user("Administrador").roles("ADMIN")).with(csrf()).contentType("application/json").content("{\"senha\":\"123456\"}")).andExpect(status().isNoContent());
    }

    @Test void apagarInexistenteRetorna404() throws Exception {
        mvc.perform(delete("/api/produtos/999999").with(user("Administrador").roles("ADMIN")).with(csrf())).andExpect(status().isNotFound());
    }
    @Test void senhaIncorretaPreservaProdutosEHistoricos() throws Exception {
        criar("Preservado");
        mvc.perform(delete("/api/produtos").with(user("Administrador").roles("ADMIN")).with(csrf()).contentType("application/json").content("{\"senha\":\"errada\"}")).andExpect(status().isForbidden());
        assertEquals(1, produtos.count()); assertEquals(2, movimentacoes.count());
    }
    @Test void semSenhaNaoPermiteLimpeza() throws Exception {
        criar("Preservado");
        mvc.perform(delete("/api/produtos").with(user("Administrador").roles("ADMIN")).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/produtos").with(user("Administrador").roles("ADMIN")).with(csrf()).contentType("application/json").content("{\"senha\":\"\"}")).andExpect(status().isBadRequest());
        assertEquals(1, produtos.count()); assertEquals(2, movimentacoes.count());
    }

    @Test void acessoSemLoginNaoPermitePainelNemApi() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/api/produtos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/login.html")).andExpect(status().isOk());
        mvc.perform(get("/csrf")).andExpect(status().isOk());
    }
    @Test void credenciaisCorretasAbremSessaoELogoutEncerra() throws Exception {
        var resultado = mvc.perform(post("/login").with(csrf())
            .param("username", "Administrador").param("password", "123456"))
            .andExpect(redirectedUrl("/")).andReturn();
        MockHttpSession sessao = (MockHttpSession) resultado.getRequest().getSession(false);
        assertNotNull(sessao);
        mvc.perform(get("/api/produtos").session(sessao)).andExpect(status().isOk());
        mvc.perform(post("/logout").session(sessao).with(csrf()))
            .andExpect(redirectedUrl("/login.html?logout"));
        assertTrue(sessao.isInvalid());
        mvc.perform(get("/api/produtos")).andExpect(status().isUnauthorized());
    }
    @Test void senhaIncorretaNoLoginNaoAbreSessao() throws Exception {
        mvc.perform(post("/login").with(csrf()).param("username", "Administrador")
            .param("password", "errada")).andExpect(redirectedUrl("/login.html?error"));
    }
    @Test void semCsrfNaoPermiteAlterarEstoque() throws Exception {
        criar("Preservado");
        mvc.perform(delete("/api/produtos").with(user("Administrador").roles("ADMIN"))
            .contentType("application/json").content("{\"senha\":\"123456\"}"))
            .andExpect(status().isForbidden());
        assertEquals(1, produtos.count());
    }
    @Test void contextoIaLeEstoqueEHistoricoSemAlterarDados() {
        Produto produto = criar("Refrigerante");
        var contexto = contextoIa.consultar(30);
        assertEquals(1, contexto.produtos().size());
        var item = contexto.produtos().get(0);
        assertEquals(produto.id, item.id()); assertEquals(32, item.saldo());
        assertEquals(40, item.movimentacoesDiarias().get(0).entradas());
        assertEquals(8, item.movimentacoesDiarias().get(0).saidas());
        assertNotNull(contexto.primeiraMovimentacaoRegistrada());
        assertEquals(1, produtos.count()); assertEquals(2, movimentacoes.count());
    }
    @Test void assistenteProtegidoEInformaAusenciaDeChave() throws Exception {
        mvc.perform(get("/api/assistente/status")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/assistente/perguntas").with(user("Administrador").roles("ADMIN")).with(csrf())
            .contentType("application/json").content("{\"pergunta\":\"Quais produtos estão acabando?\",\"periodoDias\":30}"))
            .andExpect(status().isServiceUnavailable());
        mvc.perform(post("/api/assistente/perguntas").with(user("Administrador").roles("ADMIN")).with(csrf())
            .contentType("application/json").content("{\"pergunta\":\"\",\"periodoDias\":120}"))
            .andExpect(status().isBadRequest());
    }
}
