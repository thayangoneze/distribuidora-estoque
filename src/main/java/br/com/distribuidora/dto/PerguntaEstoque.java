package br.com.distribuidora.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record PerguntaEstoque(@NotBlank @Size(max = 2000) String pergunta,
    @Min(1) @Max(90) int periodoDias,
    @Size(max = 8) List<@NotNull @Valid Mensagem> historico) {
    public record Mensagem(@NotNull Papel papel, @NotBlank @Size(max = 6000) String texto) {}
    public enum Papel { USUARIO, ASSISTENTE }
}
