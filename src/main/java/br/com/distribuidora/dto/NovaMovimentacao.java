package br.com.distribuidora.dto;

import br.com.distribuidora.model.Movimentacao;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record NovaMovimentacao(@NotNull Movimentacao.Tipo tipo, @Min(1) int quantidade) {}
