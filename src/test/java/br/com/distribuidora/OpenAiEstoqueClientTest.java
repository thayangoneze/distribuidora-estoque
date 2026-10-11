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
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.hamcrest.Matchers.containsString;

class OpenAiEstoqueClientTest {
    private final ContextoEstoqueService.Contexto contexto = new ContextoEstoqueService.Contexto(
        "2026-10-11T23:00:00Z", "2026-09-12", 30, null,
        List.of(new ContextoEstoqueService.Item(1L, "Refrigerante", 32, 10, 3, 7,
            List.of(new ContextoEstoqueService.Dia("2026-10-11", 40, 8)))), List.of());
    private final PerguntaEstoque pergunta = new PerguntaEstoque("Quanto saiu?", 30, List.of());

    @Test void enviaDadosReaisEExtraiTextoSemDependerDaPrimeiraPosicao() {
        var builder = RestClient.builder().baseUrl("https://api.openai.com/v1");
        var servidor = MockRestServiceServer.bindTo(builder).build();
        var ia = new OpenAiEstoqueClient(builder.build(), new ObjectMapper(), "chave-de-teste", "gpt-4.1-mini");
        servidor.expect(requestTo("https://api.openai.com/v1/responses"))
            .andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", "Bearer chave-de-teste"))
            .andExpect(jsonPath("$.store").value(false))
            .andExpect(jsonPath("$.input[0].content").value(containsString("Refrigerante")))
            .andExpect(jsonPath("$.input[0].content").value(containsString("\"saidas\":8")))
            .andRespond(withSuccess("{\"status\":\"completed\",\"output\":[{\"type\":\"reasoning\"},{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"Saíram 8 unidades.\"}]}]}", MediaType.APPLICATION_JSON));
        assertEquals("Saíram 8 unidades.", ia.responder(pergunta, contexto));
        servidor.verify();
    }
    @Test void semChaveNaoSimulaUmaRespostaNemChamaServico() {
        var ia = new OpenAiEstoqueClient(RestClient.create(), new ObjectMapper(), "", "gpt-4.1-mini");
        assertFalse(ia.configurado());
        assertEquals(503, assertThrows(ResponseStatusException.class, () -> ia.responder(pergunta, contexto)).getStatusCode().value());
    }
    @Test void limiteDoProvedorRetornaMensagemSemExporChave() {
        var builder = RestClient.builder().baseUrl("https://api.openai.com/v1");
        var servidor = MockRestServiceServer.bindTo(builder).build();
        var ia = new OpenAiEstoqueClient(builder.build(), new ObjectMapper(), "segredo", "gpt-4.1-mini");
        servidor.expect(requestTo("https://api.openai.com/v1/responses")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        var erro = assertThrows(ResponseStatusException.class, () -> ia.responder(pergunta, contexto));
        assertEquals(503, erro.getStatusCode().value());
        assertFalse(erro.getReason().contains("segredo")); servidor.verify();
    }
    @Test void respostaIncompletaNaoEhApresentadaComoConcluida() {
        var builder = RestClient.builder().baseUrl("https://api.openai.com/v1");
        var servidor = MockRestServiceServer.bindTo(builder).build();
        var ia = new OpenAiEstoqueClient(builder.build(), new ObjectMapper(), "teste", "gpt-4.1-mini");
        servidor.expect(requestTo("https://api.openai.com/v1/responses"))
            .andRespond(withSuccess("{\"status\":\"incomplete\",\"output\":[]}", MediaType.APPLICATION_JSON));
        assertEquals(502, assertThrows(ResponseStatusException.class, () -> ia.responder(pergunta, contexto)).getStatusCode().value());
        servidor.verify();
    }
}
