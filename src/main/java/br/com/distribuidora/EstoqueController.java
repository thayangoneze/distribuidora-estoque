package br.com.distribuidora;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
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
    public record NovaMovimentacao(@NotNull Movimentacao.Tipo tipo, @Min(1) int quantidade) {}
    @PostMapping("/produtos/{id}/movimentacoes")
    public Produto movimentar(@PathVariable Long id, @Valid @RequestBody NovaMovimentacao dados) {
        return estoque.movimentar(id, dados.tipo(), dados.quantidade());
    }
    @GetMapping("/reposicao")
    public List<EstoqueService.Sugestao> reposicao(@RequestParam(defaultValue = "30") int dias) {
        return estoque.sugestoes(dias);
    }
}
