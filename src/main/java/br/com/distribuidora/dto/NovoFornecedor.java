package br.com.distribuidora.dto;
import jakarta.validation.constraints.*;
public record NovoFornecedor(@NotBlank @Size(max=255) String nome) {}
