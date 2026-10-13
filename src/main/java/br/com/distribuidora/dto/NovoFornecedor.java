package br.com.distribuidora.dto;
import jakarta.validation.constraints.*;
public record NovoFornecedor(@NotBlank @Size(max=255) String nome,
    @Size(max=7) java.util.Set<@NotNull @Min(1) @Max(7) Integer> diasEntrega) {}
