package br.com.distribuidora.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;

public record PrecosProduto(
    @DecimalMin("0") @Digits(integer=12, fraction=2) BigDecimal precoCusto,
    @DecimalMin("0") @Digits(integer=12, fraction=2) BigDecimal precoVenda,
    @DecimalMin("0") @DecimalMax("100000") @Digits(integer=6,fraction=2) BigDecimal percentualAcrescimo,
    boolean calcularPrecoVenda) {}
