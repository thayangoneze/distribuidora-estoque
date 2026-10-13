package br.com.distribuidora.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
public record CadastroLote(@NotNull UUID id,@AssertTrue boolean revisado,boolean criarFornecedores,
    @NotNull @Size(min=1,max=200) List<@NotNull @Valid Item> itens) {
    public record Item(@NotBlank @Size(max=255) String nome,@NotNull @Pattern(regexp="UN|CX|PCT") String unidadeEstoque,
        @Min(0) int saldo,@Min(0) int estoqueSeguranca,
        @DecimalMin("0") @Digits(integer=12,fraction=2) BigDecimal precoCusto,
        @DecimalMin("0") @Digits(integer=12,fraction=2) BigDecimal precoVenda,
        @DecimalMin("0") @DecimalMax("100000") @Digits(integer=6,fraction=2) BigDecimal percentualAcrescimo,
        boolean calcularPrecoVenda,
        @Size(max=20) Set<@NotBlank @Size(max=255) String> fornecedores) {}
}
