package br.com.distribuidora.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record ConfirmacaoNota(@Size(max=255) String fornecedor, @Size(max=100) String numeroNota,
    @Size(max=10) String dataEmissao, @AssertTrue boolean revisado,
    @NotNull @Size(min=1,max=100) List<@NotNull @Valid Item> itens) {
    public record Item(Long produtoId, @NotBlank @Size(max=255) String nome,
        @NotBlank @Pattern(regexp="UN|CX|PCT") String unidadeEstoque,
        @Min(1) @Max(100000000) int quantidadeEstoque,
        @DecimalMin(value="0",inclusive=false) @Digits(integer=9,fraction=4) BigDecimal quantidadeNota,
        @Size(max=32) String unidadeNota,
        @DecimalMin("0") @Digits(integer=12,fraction=6) BigDecimal valorUnitarioNota,
        @Min(0) int estoqueSeguranca, @Min(0) int prazoEntregaDias, @Min(0) int coberturaDesejadaDias) {}
}
