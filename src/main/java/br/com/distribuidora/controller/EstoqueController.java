package br.com.distribuidora.controller;

import br.com.distribuidora.dto.NovaMovimentacao;
import br.com.distribuidora.dto.PrecosProduto;
import br.com.distribuidora.dto.ConfirmacaoLimpeza;
import br.com.distribuidora.dto.Sugestao;
import br.com.distribuidora.model.Produto;
import br.com.distribuidora.repository.ProdutoRepository;
import br.com.distribuidora.service.EstoqueService;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class EstoqueController {
    private final ProdutoRepository produtos;
    private final EstoqueService estoque;
    @Value("${estoque.senha-administrador}")
    private String senhaAdministrador;
    public EstoqueController(ProdutoRepository produtos, EstoqueService estoque) { this.produtos = produtos; this.estoque = estoque; }
    @PostMapping("/produtos") @ResponseStatus(HttpStatus.CREATED)
    public Produto criar(@Valid @RequestBody Produto produto) {
        produto.id = null; produto.versao = null; produto.saldo = 0;
        return produtos.save(produto);
    }
    @GetMapping("/produtos") public List<Produto> listar() { return produtos.findAll(); }
    @PutMapping("/produtos/{id}/precos")
    public Produto atualizarPrecos(@PathVariable Long id, @Valid @RequestBody PrecosProduto dados) {
        return estoque.atualizarPrecos(id, dados);
    }
    @DeleteMapping("/produtos/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void apagar(@PathVariable Long id) { estoque.apagarProduto(id); }
    @DeleteMapping("/produtos") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void limpar(@Valid @RequestBody ConfirmacaoLimpeza confirmacao) {
        if (!senhaAdministrador.equals(confirmacao.senha()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Senha do administrador incorreta");
        estoque.limparEstoque();
    }
    @PostMapping("/produtos/{id}/movimentacoes")
    public Produto movimentar(@PathVariable Long id, @Valid @RequestBody NovaMovimentacao dados) {
        return estoque.movimentar(id, dados.tipo(), dados.quantidade());
    }
    @GetMapping("/reposicao")
    public List<Sugestao> reposicao(@RequestParam(defaultValue = "30") int dias) {
        return estoque.sugestoes(dias);
    }
}
