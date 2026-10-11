package br.com.distribuidora.controller;

import br.com.distribuidora.dto.NovaMovimentacao;
import br.com.distribuidora.dto.Sugestao;
import br.com.distribuidora.model.Produto;
import br.com.distribuidora.repository.ProdutoRepository;
import br.com.distribuidora.service.EstoqueService;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.List;

@RestController
@RequestMapping("/api")
public class EstoqueController {
    private final ProdutoRepository produtos;
    private final EstoqueService estoque;
    public EstoqueController(ProdutoRepository produtos, EstoqueService estoque) { this.produtos = produtos; this.estoque = estoque; }
    @PostMapping("/produtos") @ResponseStatus(HttpStatus.CREATED)
    public Produto criar(@Valid @RequestBody Produto produto) {
        produto.id = null; produto.versao = null; produto.saldo = 0;
        return produtos.save(produto);
    }
    @GetMapping("/produtos") public List<Produto> listar() { return produtos.findAll(); }
    @DeleteMapping("/produtos/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void apagar(@PathVariable Long id) { estoque.apagarProduto(id); }
    @DeleteMapping("/produtos") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void limpar() { estoque.limparEstoque(); }
    @PostMapping("/produtos/{id}/movimentacoes")
    public Produto movimentar(@PathVariable Long id, @Valid @RequestBody NovaMovimentacao dados) {
        return estoque.movimentar(id, dados.tipo(), dados.quantidade());
    }
    @GetMapping("/reposicao")
    public List<Sugestao> reposicao(@RequestParam(defaultValue = "30") int dias) {
        return estoque.sugestoes(dias);
    }
}
