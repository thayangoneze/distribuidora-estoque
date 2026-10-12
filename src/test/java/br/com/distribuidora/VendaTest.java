package br.com.distribuidora;
import br.com.distribuidora.model.*;
import br.com.distribuidora.repository.*;
import br.com.distribuidora.service.EstoqueService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:vendas;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop","estoque.ia.api-key="})
@AutoConfigureMockMvc
@TestExecutionListeners(listeners=DependencyInjectionTestExecutionListener.class,mergeMode=TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
class VendaTest {
    @Autowired br.com.distribuidora.service.VendaService vendaService;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;
    @org.springframework.beans.factory.annotation.Value("${estoque.senha-administrador}") String senhaTeste;
    @Autowired MockMvc mvc;
    @Autowired EstoqueService estoque;
    @Autowired ProdutoRepository produtos;
    @Autowired MovimentacaoRepository movimentos;
    @Autowired VendaRepository vendas;
    @BeforeEach void limpar() { movimentos.deleteAllInBatch();produtos.deleteAll();vendas.deleteAll(); }
    Produto produto(String nome,int saldo,String codigo) {
        Produto p=new Produto();p.nome=nome;p.saldo=saldo;p.codigoBarras=codigo;p.precoVenda=new BigDecimal("10.00");return estoque.criar(p);
    }
    String pedido(UUID id,Produto p,int quantidade,String pagamento,String desconto,String recebido) {
        return "{\"id\":\""+id+"\",\"pagamento\":\""+pagamento+"\",\"desconto\":"+desconto+",\"valorRecebido\":"+recebido+",\"pagamentoConfirmado\":true,\"itens\":[{\"produtoId\":"+p.id+",\"quantidade\":"+quantidade+",\"precoUnitario\":10.00}]}";
    }
    void enviar(String body,int codigo) throws Exception {
        mvc.perform(post("/api/vendas").with(user("Administrador").roles("ADMIN")).with(csrf()).contentType("application/json").content(body))
            .andExpect(status().is(codigo));
    }
    @Test void dinheiroDescontoTrocoBaixaAtomicamenteENaoRepeteAoReenviar() throws Exception {
        Produto p=produto("Água",10,"0123456789012");UUID id=UUID.randomUUID();String json=pedido(id,p,3,"DINHEIRO","2.50","50");
        enviar(json,200);enviar(json,200);
        assertEquals(7,produtos.findById(p.id).orElseThrow().saldo);assertEquals(1,vendas.count());
        Venda v=vendas.findById(id).orElseThrow();assertEquals(0,new BigDecimal("27.50").compareTo(v.total));assertEquals(0,new BigDecimal("22.50").compareTo(v.troco));
        assertEquals(1,movimentos.findAll().stream().filter(m->m.tipo==Movimentacao.Tipo.SAIDA).count());
        assertEquals(id,movimentos.findAll().stream().filter(m->m.tipo==Movimentacao.Tipo.SAIDA).findFirst().orElseThrow().vendaId);
        enviar(pedido(id,p,2,"DINHEIRO","0","50"),409);assertEquals(7,produtos.findById(p.id).orElseThrow().saldo);
        estoque.apagarProduto(p.id);assertEquals("Água",vendas.findById(id).orElseThrow().itens.get(0).nome);
    }
    @Test void pixECartaoRegistramPagamentoManualSemTroco() throws Exception {
        Produto p=produto("Refri",10,null);
        for(String tipo:List.of("PIX","CARTAO"))enviar(pedido(UUID.randomUUID(),p,1,tipo,"0","null"),200);
        assertEquals(8,produtos.findById(p.id).orElseThrow().saldo);
        assertTrue(vendas.findAll().stream().allMatch(v->v.troco.signum()==0 && v.valorRecebido==null));
    }
    @Test void precoMudouOuSaldoInsuficienteNaoBaixaNada() throws Exception {
        Produto p=produto("Refri",2,null);Produto outro=produto("Água",5,null);
        String primeiro=pedido(UUID.randomUUID(),outro,1,"PIX","0","null");
        String dois=primeiro.replace("]}",",{\"produtoId\":"+p.id+",\"quantidade\":3,\"precoUnitario\":10.00}]}");
        enviar(dois,409);assertEquals(5,produtos.findById(outro.id).orElseThrow().saldo);assertEquals(2,produtos.findById(p.id).orElseThrow().saldo);assertEquals(0,vendas.count());
        enviar(pedido(UUID.randomUUID(),p,1,"PIX","0","null").replace("10.00","9.00"),409);assertEquals(0,vendas.count());
    }
    @Test void pagamentoNaoConfirmadoDinheiroInsuficienteEDescontoExcessivoRejeitados() throws Exception {
        Produto p=produto("Água",10,null);
        enviar(pedido(UUID.randomUUID(),p,1,"PIX","0","null").replace("true","false"),400);
        enviar(pedido(UUID.randomUUID(),p,1,"DINHEIRO","0","9"),400);
        enviar(pedido(UUID.randomUUID(),p,1,"PIX","11","null"),400);
        enviar(pedido(UUID.randomUUID(),p,0,"PIX","0","null"),400);
        assertEquals(10,produtos.findById(p.id).orElseThrow().saldo);assertEquals(0,vendas.count());
    }
    @Test void codigoMantemZerosNaoDuplicaEPodeSerEditado() throws Exception {
        Produto p=produto("Água",0,"0123456789012");assertEquals("0123456789012",produtos.findById(p.id).orElseThrow().codigoBarras);
        mvc.perform(post("/api/produtos").with(user("Administrador").roles("ADMIN")).with(csrf()).contentType("application/json")
            .content("{\"nome\":\"Outro\",\"codigoBarras\":\"0123456789012\"}")).andExpect(status().isConflict());
        mvc.perform(put("/api/produtos/"+p.id+"/codigo-barras").with(user("Administrador").roles("ADMIN")).with(csrf()).contentType("application/json")
            .content("{\"codigoBarras\":\"7891234567890\"}")).andExpect(status().isOk());
        assertEquals("7891234567890",produtos.findById(p.id).orElseThrow().codigoBarras);
    }
    @Test void duasVendasConcorrentesNaoVendemMaisQueSaldo() throws Exception {
        Produto p=produto("Últimas unidades",5,null);
        var inicio=new java.util.concurrent.CountDownLatch(1);
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.List<java.util.concurrent.Future<Integer>> resultados=new java.util.ArrayList<>();
            for(int n=0;n<2;n++) {
                var dados=mapper.readValue(pedido(UUID.randomUUID(),p,4,"PIX","0","null"),br.com.distribuidora.dto.NovaVenda.class);
                resultados.add(executor.submit(() -> {
                    inicio.await();
                    try { vendaService.finalizar(dados);return 200; }
                    catch(org.springframework.web.server.ResponseStatusException e){return e.getStatusCode().value();}
                }));
            }
            inicio.countDown();
            var codigos=new java.util.ArrayList<Integer>();
            for(var resultado:resultados)codigos.add(resultado.get(10,java.util.concurrent.TimeUnit.SECONDS));
            codigos.sort(Integer::compareTo);assertEquals(List.of(200,409),codigos);
            assertEquals(1,produtos.findById(p.id).orElseThrow().saldo);assertEquals(1,vendas.count());
        } finally { executor.shutdownNow(); }
    }
    @Test void leitorProtegidoCsrfQrEConexaoPorSessoesDiferentes() throws Exception {
        mvc.perform(get("/api/leitores/"+UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/leitores").with(user("Administrador").roles("ADMIN"))).andExpect(status().isForbidden());
        var pc=new org.springframework.mock.web.MockHttpSession();var phone=new org.springframework.mock.web.MockHttpSession();
        var res=mvc.perform(post("/api/leitores").session(pc).with(user("Administrador").roles("ADMIN")).with(csrf())).andExpect(status().isOk()).andReturn();
        String id=mapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(get("/api/leitores/"+id+"/qr").param("origem","http://localhost:8081").session(pc).with(user("Administrador").roles("ADMIN")))
            .andExpect(status().isBadRequest());
        var imagem=mvc.perform(get("/api/leitores/"+id+"/qr").param("origem","https://example.com").session(pc).with(user("Administrador").roles("ADMIN")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertEquals((byte)0x89,imagem[0]);assertEquals((byte)'P',imagem[1]);
        mvc.perform(post("/api/leitores/"+id+"/conectar").session(phone).with(user("Administrador").roles("ADMIN")).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/leitores/"+id+"/leituras").session(phone).with(user("Administrador").roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/leitores/"+id+"/leituras").session(phone).with(user("Administrador").roles("ADMIN")).with(csrf()).contentType("application/json")
            .content("{\"id\":\""+UUID.randomUUID()+"\",\"codigo\":\"abc\"}")).andExpect(status().isBadRequest());
    }
    @Test void loginRetornaAoLeitorHttpsAposAbrirQrCode() throws Exception {
        var sessao=new org.springframework.mock.web.MockHttpSession();String id=UUID.randomUUID().toString();
        mvc.perform(get("/leitor.html?conexao="+id).header("X-Forwarded-Proto","https").header("X-Forwarded-Host","example.com").session(sessao))
            .andExpect(status().is3xxRedirection());
        var resposta=mvc.perform(post("/login").session(sessao).with(csrf()).param("username","Administrador")
            .param("password",senhaTeste)).andExpect(status().is3xxRedirection()).andReturn();
        assertTrue(resposta.getResponse().getRedirectedUrl().startsWith("https://example.com/leitor.html?conexao="+id),resposta.getResponse().getRedirectedUrl());
    }
    @Test void vendaExigeLoginECsrf() throws Exception {
        mvc.perform(get("/api/vendas")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/vendas").with(user("Administrador").roles("ADMIN")).contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
    }
}
