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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:exclusao;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "estoque.senha-administrador=123456"
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

    private Produto criar(String nome) {
        Produto p = new Produto(); p.nome = nome;
        p = produtos.saveAndFlush(p);
        estoque.movimentar(p.id, Movimentacao.Tipo.ENTRADA, 40);
        estoque.movimentar(p.id, Movimentacao.Tipo.SAIDA, 8);
        return p;
    }

    @Test void apagarUmProdutoRemoveSeuHistoricoEPreservaOutro() throws Exception {
        Produto primeiro = criar("Primeiro");
        Produto outro = criar("Outro");
        mvc.perform(delete("/api/produtos/" + primeiro.id)).andExpect(status().isNoContent());
        produtos.flush();
        assertFalse(produtos.existsById(primeiro.id));
        assertTrue(produtos.existsById(outro.id));
        assertEquals(2, movimentacoes.count());
        assertTrue(movimentacoes.findAll().stream().allMatch(m -> m.produto.id.equals(outro.id)));
    }

    @Test void limparRemoveProdutosEHistoricosEPodeSerRepetido() throws Exception {
        criar("Primeiro"); criar("Outro");
        mvc.perform(delete("/api/produtos").contentType("application/json").content("{\"senha\":\"123456\"}")).andExpect(status().isNoContent());
        assertEquals(0, produtos.count()); assertEquals(0, movimentacoes.count());
        assertTrue(estoque.sugestoes(30).isEmpty());
        mvc.perform(delete("/api/produtos").contentType("application/json").content("{\"senha\":\"123456\"}")).andExpect(status().isNoContent());
    }

    @Test void apagarInexistenteRetorna404() throws Exception {
        mvc.perform(delete("/api/produtos/999999")).andExpect(status().isNotFound());
    }
    @Test void senhaIncorretaPreservaProdutosEHistoricos() throws Exception {
        criar("Preservado");
        mvc.perform(delete("/api/produtos").contentType("application/json").content("{\"senha\":\"errada\"}")).andExpect(status().isForbidden());
        assertEquals(1, produtos.count()); assertEquals(2, movimentacoes.count());
    }
    @Test void semSenhaNaoPermiteLimpeza() throws Exception {
        criar("Preservado");
        mvc.perform(delete("/api/produtos")).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/produtos").contentType("application/json").content("{\"senha\":\"\"}")).andExpect(status().isBadRequest());
        assertEquals(1, produtos.count()); assertEquals(2, movimentacoes.count());
    }
}
