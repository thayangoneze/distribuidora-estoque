package br.com.distribuidora.controller;
import br.com.distribuidora.dto.NovoFornecedor;
import br.com.distribuidora.model.Fornecedor;
import br.com.distribuidora.service.FornecedorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.List;
@RestController
@RequestMapping("/api/fornecedores")
public class FornecedorController {
    private final FornecedorService service;
    public FornecedorController(FornecedorService service) { this.service=service; }
    @GetMapping public List<Fornecedor> listar() { return service.listar(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Fornecedor criar(@Valid @RequestBody NovoFornecedor dados) { return service.criar(dados.nome()); }
}
