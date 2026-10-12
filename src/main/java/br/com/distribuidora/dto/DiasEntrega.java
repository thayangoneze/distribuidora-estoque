package br.com.distribuidora.dto;
import java.util.Set;
import jakarta.validation.constraints.*;
public record DiasEntrega(@NotNull Set<@NotNull @Min(1) @Max(7) Integer> diasEntrega) {}
