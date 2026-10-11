package br.com.distribuidora.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmacaoLimpeza(@NotBlank String senha) {}
