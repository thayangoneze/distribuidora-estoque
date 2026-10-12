package br.com.distribuidora;

import br.com.distribuidora.service.*;
import br.com.distribuidora.dto.PerguntaEstoque;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.hamcrest.Matchers.containsString;

class GeminiEstoqueClientTest {
    private static final String BASE = "https://generativelanguage.googleapis.com/v1beta";
    private static final String URL = BASE + "/models/gemini-3.5-flash-lite:generateContent";
    private final ContextoEstoqueService.Contexto contexto = new ContextoEstoqueService.Contexto(
        "2026-10-11T23:00:00Z", "2026-09-12", 30, null,
        List.of(new ContextoEstoqueService.Item(1L, "Refrigerante", "UN", 32, 10, 3, 7,
            List.of(new ContextoEstoqueService.Dia("2026-10-11", 40, 8)))), List.of());
    private final PerguntaEstoque pergunta = new PerguntaEstoque("Quanto saiu?", 30, List.of());

    @Test void enviaDadosReaisEIgnoraPensamentoNaResposta() {
        var builder = RestClient.builder().baseUrl(BASE);
        var servidor = MockRestServiceServer.bindTo(builder).build();
        var ia = new GeminiEstoqueClient(builder.build(), new ObjectMapper(), "chave-de-teste", "gemini-3.5-flash-lite");
        servidor.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
            .andExpect(header("x-goog-api-key", "chave-de-teste"))
            .andExpect(headerDoesNotExist("Authorization"))
            .andExpect(jsonPath("$.contents[0].parts[0].text").value(containsString("Refrigerante")))
            .andExpect(jsonPath("$.contents[0].parts[0].text").value(containsString("\"saidas\":8")))
            .andExpect(jsonPath("$.systemInstruction.parts[0].text").value(containsString("Não invente")))
            .andRespond(withSuccess("{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"thought\":true,\"text\":\"Pensamento interno\"},{\"text\":\"Saíram 8 unidades.\"}]}}]}", MediaType.APPLICATION_JSON));
        assertEquals("Saíram 8 unidades.", ia.responder(pergunta, contexto));
        servidor.verify();
    }
    @Test void conversaUsaPapeisUserEModelComDadosAtualizadosNaPerguntaFinal() {
        var builder = RestClient.builder().baseUrl(BASE);
        var servidor = MockRestServiceServer.bindTo(builder).build();
        var ia = new GeminiEstoqueClient(builder.build(), new ObjectMapper(), "teste", "gemini-3.5-flash-lite");
        servidor.expect(requestTo(URL)).andExpect(jsonPath("$.contents[0].role").value("user"))
            .andExpect(jsonPath("$.contents[1].role").value("model"))
            .andExpect(jsonPath("$.contents[2].parts[0].text").value(containsString("Refrigerante")))
            .andRespond(withSuccess("{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"Resposta\"}]}}]}", MediaType.APPLICATION_JSON));
        ia.responder(new PerguntaEstoque("E agora?", 30, List.of(
            new PerguntaEstoque.Mensagem(PerguntaEstoque.Papel.USUARIO, "Primeira pergunta"),
            new PerguntaEstoque.Mensagem(PerguntaEstoque.Papel.ASSISTENTE, "Primeira resposta"))), contexto);
        servidor.verify();
    }
    @Test void semChaveNaoSimulaRespostaNemChamaServico() {
        var ia = new GeminiEstoqueClient(RestClient.create(), new ObjectMapper(), "", "gemini-3.5-flash-lite");
        assertFalse(ia.configurado());
        assertEquals(503, assertThrows(ResponseStatusException.class, () -> ia.responder(pergunta, contexto)).getStatusCode().value());
    }
    @Test void errosDoProvedorNaoExpoemChaveEExplicamAcao() {
        verificarErro(429, 503, "cota diária");
        verificarErro(403, 502, "permissões");
        verificarErro(404, 502, "modelo");
    }
    private void verificarErro(int status, int esperado, String trecho) {
        var builder = RestClient.builder().baseUrl(BASE);
        var servidor = MockRestServiceServer.bindTo(builder).build();
        var ia = new GeminiEstoqueClient(builder.build(), new ObjectMapper(), "segredo", "gemini-3.5-flash-lite");
        servidor.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.valueOf(status)));
        var erro = assertThrows(ResponseStatusException.class, () -> ia.responder(pergunta, contexto));
        assertEquals(esperado, erro.getStatusCode().value());
        assertTrue(erro.getReason().contains(trecho)); assertFalse(erro.getReason().contains("segredo"));
        servidor.verify();
    }
    @Test void respostaIncompletaNaoEhApresentadaComoConcluida() {
        var builder = RestClient.builder().baseUrl(BASE);
        var servidor = MockRestServiceServer.bindTo(builder).build();
        var ia = new GeminiEstoqueClient(builder.build(), new ObjectMapper(), "teste", "gemini-3.5-flash-lite");
        servidor.expect(requestTo(URL)).andRespond(withSuccess("{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\",\"content\":{\"parts\":[{\"text\":\"Texto parcial\"}]}}]}", MediaType.APPLICATION_JSON));
        assertEquals(502, assertThrows(ResponseStatusException.class, () -> ia.responder(pergunta, contexto)).getStatusCode().value());
        servidor.verify();
    }
    @Test void respostaSemCandidatosInformaFalha() {
        var builder = RestClient.builder().baseUrl(BASE);
        var servidor = MockRestServiceServer.bindTo(builder).build();
        var ia = new GeminiEstoqueClient(builder.build(), new ObjectMapper(), "teste", "gemini-3.5-flash-lite");
        servidor.expect(requestTo(URL)).andRespond(withSuccess("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}", MediaType.APPLICATION_JSON));
        assertEquals(502, assertThrows(ResponseStatusException.class, () -> ia.responder(pergunta, contexto)).getStatusCode().value());
        servidor.verify();
    }
    @Test void extraiNotaComSchemaSemConverterCaixasEmUnidades() throws Exception {
        var builder=RestClient.builder().baseUrl(BASE);
        var servidor=MockRestServiceServer.bindTo(builder).build();
        var ia=new GeminiEstoqueClient(builder.build(),new ObjectMapper(),"teste","gemini-3.5-flash-lite");
        String nota="{\"fornecedor\":\"Distribuidor\",\"numeroNota\":\"10\",\"dataEmissao\":null,\"observacoes\":null,\"itens\":[{\"descricao\":\"Refrigerante\",\"quantidadeNota\":10,\"unidadeNota\":\"CX\",\"valorUnitarioNota\":50.25,\"observacao\":null}]}";
        String resposta=new ObjectMapper().writeValueAsString(Map.of("candidates",List.of(Map.of("finishReason","STOP",
            "content",Map.of("parts",List.of(Map.of("text",nota)))))));
        servidor.expect(requestTo(URL))
            .andExpect(jsonPath("$.contents[0].parts[1].inlineData.mimeType").value("application/pdf"))
            .andExpect(jsonPath("$.generationConfig.responseFormat.text.mimeType").value("application/json"))
            .andExpect(jsonPath("$.generationConfig.responseFormat.text.schema.properties.itens.type").value("array"))
            .andRespond(withSuccess(resposta,MediaType.APPLICATION_JSON));
        var extraida=ia.extrairNota("%PDF-1.7".getBytes(),"application/pdf");
        assertEquals("CX",extraida.itens().get(0).unidadeNota());
        assertEquals(10,extraida.itens().get(0).quantidadeNota().intValueExact());
        servidor.verify();
    }
}
