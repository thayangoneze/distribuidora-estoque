package br.com.distribuidora.controller;
import br.com.distribuidora.dto.CadastroLote;
import br.com.distribuidora.service.CadastroLoteService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/produtos/lote")
public class CadastroLoteController {
    private final CadastroLoteService service;
    public CadastroLoteController(CadastroLoteService service){this.service=service;}
    @PostMapping public CadastroLoteService.Resultado cadastrar(@Valid @RequestBody CadastroLote lote){return service.cadastrar(lote);}
}
