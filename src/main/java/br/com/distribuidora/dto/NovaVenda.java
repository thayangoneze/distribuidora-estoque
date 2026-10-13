package br.com.distribuidora.dto;
import br.com.distribuidora.model.Venda.Pagamento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
public record NovaVenda(@NotNull UUID id, @NotNull Pagamento pagamento,
    @NotNull @DecimalMin("0") @Digits(integer=12,fraction=2) BigDecimal desconto,
    @DecimalMin("0") @Digits(integer=12,fraction=2) BigDecimal valorRecebido,
    @AssertTrue boolean pagamentoConfirmado,
    @NotNull @Size(min=1,max=100) List<@NotNull @Valid Item> itens) {
    public record Item(@NotNull @Positive Long produtoId, @Min(1) @Max(1000000) int quantidade,
        @NotNull @DecimalMin("0") @Digits(integer=12,fraction=2) BigDecimal precoUnitario) {}
}
