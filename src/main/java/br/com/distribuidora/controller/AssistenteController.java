package br.com.distribuidora.controller;

import br.com.distribuidora.dto.PerguntaEstoque;
import br.com.distribuidora.service.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/assistente")
public class AssistenteController {
    private final ContextoEstoqueService contexto;
    private final OpenAiEstoqueClient ia;
    public AssistenteController(ContextoEstoqueService contexto, OpenAiEstoqueClient ia) { this.contexto = contexto; this.ia = ia; }
    @GetMapping("/status")
    public Map<String, Boolean> status() { return Map.of("configurado", ia.configurado()); }
    public record Resposta(String resposta, String atualizadoEm, int periodoDias) {}
    @PostMapping("/perguntas")
    public Resposta perguntar(@Valid @RequestBody PerguntaEstoque pergunta) {
        var dados = contexto.consultar(pergunta.periodoDias());
        return new Resposta(ia.responder(pergunta, dados), dados.atualizadoEm(), dados.periodoDias());
    }
}
