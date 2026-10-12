package br.com.distribuidora.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record NotaExtraida(@Size(max=255) String fornecedor, @Size(max=100) String numeroNota,
    @Size(max=10) String dataEmissao, @NotNull @Size(min=1,max=100) List<@NotNull @Valid Item> itens,
    @Size(max=2000) String observacoes) {
    public record Item(@NotBlank @Size(max=255) String descricao,
        @DecimalMin(value="0", inclusive=false) @Digits(integer=9,fraction=4) BigDecimal quantidadeNota,
        @Size(max=32) String unidadeNota,
        @DecimalMin("0") @Digits(integer=12,fraction=6) BigDecimal valorUnitarioNota,
        @Size(max=500) String observacao) {}
}
