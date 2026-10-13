package br.com.distribuidora.controller;
import br.com.distribuidora.dto.NovaVenda;
import br.com.distribuidora.model.Venda;
import br.com.distribuidora.service.VendaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/vendas")
public class VendaController {
    private final VendaService service;
    public VendaController(VendaService service) {this.service=service;}
    @PostMapping public Venda finalizar(@Valid @RequestBody NovaVenda dados) { return service.finalizar(dados); }
    @GetMapping public List<Venda> listar() {return service.recentes();}
}
