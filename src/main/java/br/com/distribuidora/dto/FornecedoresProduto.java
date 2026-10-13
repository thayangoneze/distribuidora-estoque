package br.com.distribuidora.dto;
import jakarta.validation.constraints.*;
import java.util.Set;
public record FornecedoresProduto(@NotNull @Size(max=100) Set<@NotNull @Positive Long> fornecedorIds) {}
