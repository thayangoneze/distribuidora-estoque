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
    @Autowired br.com.distribuidora.repository.FornecedorRepository fornecedores;
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

    @Test void cadastroRegistraSaldoInicialAgendaEPrecoCalculado() throws Exception {
        var resposta=mvc.perform(post("/api/produtos").with(user("Administrador").roles("ADMIN")).with(csrf())
            .contentType("application/json").content("{\"nome\":\"Inicial\",\"saldo\":24,\"diasEntrega\":[2,5],\"precoCusto\":10,\"percentualAcrescimo\":32.5,\"calcularPrecoVenda\":true}"))
            .andExpect(status().isCreated()).andReturn();
        Long id=new com.fasterxml.jackson.databind.ObjectMapper().readTree(resposta.getResponse().getContentAsString()).get("id").asLong();
        var p=produtos.findById(id).orElseThrow(); assertEquals(24,p.saldo);
        assertEquals(java.util.Set.of(2,5),p.diasEntrega);
        assertEquals(0,new java.math.BigDecimal("13.50").compareTo(p.precoVenda));
        assertEquals(Movimentacao.Tipo.SALDO_INICIAL,movimentacoes.findAll().get(0).tipo);
        assertEquals(24,movimentacoes.findAll().get(0).quantidade);
        assertTrue(contextoIa.consultar(30).produtos().get(0).movimentacoesDiarias().isEmpty());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/produtos/"+id+"/entregas")
            .with(user("Administrador").roles("ADMIN")).with(csrf()).contentType("application/json")
            .content("{\"diasEntrega\":[1,4]}")).andExpect(status().isOk());
        assertEquals(java.util.Set.of(1,4),produtos.findById(id).orElseThrow().diasEntrega);
        assertEquals(24,produtos.findById(id).orElseThrow().saldo);
    }
    @Test void cadastroRejeitaSaldoAgendaEPercentualInvalidos() throws Exception {
        for(String extras:java.util.List.of("\"saldo\":-1","\"diasEntrega\":[8]","\"diasEntrega\":[null]",
            "\"percentualAcrescimo\":-5","\"calcularPrecoVenda\":true,\"precoCusto\":10")) {
            mvc.perform(post("/api/produtos").with(user("Administrador").roles("ADMIN")).with(csrf())
                .contentType("application/json").content("{\"nome\":\"Inválido\","+extras+"}"))
                .andExpect(status().isBadRequest());
        }
        assertEquals(0,produtos.count()); assertEquals(0,movimentacoes.count());
    }
    @Test void calculoArredondaMeioRealEPermiteVoltarAoManual() throws Exception {
        assertEquals(new java.math.BigDecimal("13.00"),EstoqueService.calcularVenda(new java.math.BigDecimal("10"),new java.math.BigDecimal("32.49")));
        assertEquals(new java.math.BigDecimal("13.50"),EstoqueService.calcularVenda(new java.math.BigDecimal("10"),new java.math.BigDecimal("32.50")));
        Produto p=criar("Preço automático");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/produtos/"+p.id+"/precos")
            .with(user("Administrador").roles("ADMIN")).with(csrf()).contentType("application/json")
            .content("{\"precoCusto\":10,\"percentualAcrescimo\":38,\"calcularPrecoVenda\":true}"))
            .andExpect(status().isOk());
        assertEquals(0,new java.math.BigDecimal("14.00").compareTo(produtos.findById(p.id).orElseThrow().precoVenda));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/produtos/"+p.id+"/precos")
            .with(user("Administrador").roles("ADMIN")).with(csrf()).contentType("application/json")
            .content("{\"precoCusto\":10,\"precoVenda\":13.99,\"calcularPrecoVenda\":false}"))
            .andExpect(status().isOk());
        assertEquals(0,new java.math.BigDecimal("13.99").compareTo(produtos.findById(p.id).orElseThrow().precoVenda));
        assertNull(produtos.findById(p.id).orElseThrow().percentualAcrescimo);
        assertEquals(32,produtos.findById(p.id).orElseThrow().saldo);
    }
    @Test void agendaConsideraProximaEntregaEIntervaloSemCoberturaManual() {
        var segunda=java.time.LocalDate.of(2026,10,12);
        assertEquals(3,EstoqueService.diasAteEntrega(java.util.Set.of(1,4),segunda));
        assertEquals(4,EstoqueService.maiorIntervaloEntrega(java.util.Set.of(1,4)));
        assertEquals(7,EstoqueService.diasAteEntrega(java.util.Set.of(1),segunda));
        assertEquals(1,EstoqueService.maiorIntervaloEntrega(java.util.Set.of(1,2,3,4,5,6,7)));
        assertEquals(0,EstoqueService.diasAteEntrega(java.util.Set.of(),segunda));
        Produto p=criar("Agenda"); p.diasEntrega=new java.util.HashSet<>(java.util.Set.of(1,4)); p.estoqueSeguranca=5;
        p.saldo=0; produtos.saveAndFlush(p);
        int espera=EstoqueService.diasAteEntrega(p.diasEntrega,java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")));
        var sugestao=estoque.sugestoes(30).get(0);
        assertEquals((int)Math.ceil((8.0/30)*espera)+5,sugestao.pontoReposicao());
        assertEquals((int)Math.ceil((8.0/30)*(espera+4))+5,sugestao.quantidadeSugerida());
    }

    @Test void limparProdutosComDiasDeEntregaRemoveAgendaSemErro() {
        Produto p=criar("Com agenda"); p.diasEntrega=new java.util.HashSet<>(java.util.Set.of(2,5)); produtos.saveAndFlush(p);
        estoque.limparEstoque(); produtos.flush();
        assertEquals(0,produtos.count()); assertEquals(0,movimentacoes.count());
    }
    private Long cadastrarFornecedor(String nome) throws Exception {
        var res=mvc.perform(post("/api/fornecedores").with(user("Administrador").roles("ADMIN")).with(csrf())
            .contentType("application/json").content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(java.util.Map.of("nome",nome))))
            .andExpect(status().isCreated()).andReturn();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }
    @Test void variasEmpresasPodemEntregarMesmoProdutoEAssistenteVeVinculos() throws Exception {
        Long ambev=cadastrarFornecedor("Ambev"); Long coca=cadastrarFornecedor("Coca-Cola");
        var res=mvc.perform(post("/api/produtos").with(user("Administrador").roles("ADMIN")).with(csrf())
            .contentType("application/json").content("{\"nome\":\"Água\",\"saldo\":12,\"fornecedorIds\":["+ambev+","+coca+"]}"))
            .andExpect(status().isCreated()).andReturn();
        Long id=new com.fasterxml.jackson.databind.ObjectMapper().readTree(res.getResponse().getContentAsString()).get("id").asLong();
        assertEquals(2,produtos.findById(id).orElseThrow().fornecedores.size());
        assertEquals(java.util.List.of("Ambev","Coca-Cola"),contextoIa.consultar(30).produtos().get(0).fornecedores());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/produtos/"+id+"/fornecedores")
            .with(user("Administrador").roles("ADMIN")).with(csrf()).contentType("application/json")
            .content("{\"fornecedorIds\":["+coca+"]}")).andExpect(status().isOk());
        assertEquals(1,produtos.findById(id).orElseThrow().fornecedores.size());
        assertEquals(12,produtos.findById(id).orElseThrow().saldo); assertEquals(1,movimentacoes.count());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/produtos/"+id+"/fornecedores")
            .with(user("Administrador").roles("ADMIN")).with(csrf()).contentType("application/json")
            .content("{\"fornecedorIds\":[]}")).andExpect(status().isOk());
        assertTrue(produtos.findById(id).orElseThrow().fornecedores.isEmpty());
        assertEquals(2,fornecedores.count());
    }
    @Test void fornecedorExigeLoginCsrfENomeUnicoEValido() throws Exception {
        mvc.perform(get("/api/fornecedores")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/fornecedores").with(user("Administrador").roles("ADMIN")).contentType("application/json").content("{\"nome\":\"Ambev\"}"))
            .andExpect(status().isForbidden());
        cadastrarFornecedor("  Ambev  ");
        mvc.perform(post("/api/fornecedores").with(user("Administrador").roles("ADMIN")).with(csrf())
            .contentType("application/json").content("{\"nome\":\"AMBEV\"}")).andExpect(status().isConflict());
        mvc.perform(post("/api/fornecedores").with(user("Administrador").roles("ADMIN")).with(csrf())
            .contentType("application/json").content("{\"nome\":\"   \"}")).andExpect(status().isBadRequest());
        assertEquals(1,fornecedores.count());
    }
    @Test void empresaInexistenteNaoCriaProdutoNemSaldoInicial() throws Exception {
        mvc.perform(post("/api/produtos").with(user("Administrador").roles("ADMIN")).with(csrf())
            .contentType("application/json").content("{\"nome\":\"Novo\",\"saldo\":12,\"fornecedorIds\":[999999]}"))
            .andExpect(status().isBadRequest());
        assertEquals(0,produtos.count()); assertEquals(0,movimentacoes.count());
        Produto p=criar("Existente");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/produtos/"+p.id+"/fornecedores")
            .with(user("Administrador").roles("ADMIN")).with(csrf()).contentType("application/json")
            .content("{\"fornecedorIds\":[999999]}")).andExpect(status().isBadRequest());
        assertTrue(produtos.findById(p.id).orElseThrow().fornecedores.isEmpty());
    }
    @Test void limparEApagarProdutosPreservaEmpresasCompartilhadas() throws Exception {
        Long id=cadastrarFornecedor("Ambev");
        Produto p=criar("Primeiro"); Produto outro=criar("Segundo");
        estoque.atualizarFornecedores(p.id,java.util.Set.of(id)); estoque.atualizarFornecedores(outro.id,java.util.Set.of(id)); produtos.flush();
        estoque.apagarProduto(p.id); produtos.flush();
        assertEquals(1,fornecedores.count()); assertEquals(1,produtos.findById(outro.id).orElseThrow().fornecedores.size());
        estoque.limparEstoque(); produtos.flush();
        assertEquals(0,produtos.count()); assertEquals(1,fornecedores.count());
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
