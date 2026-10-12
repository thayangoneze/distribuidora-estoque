package br.com.distribuidora.dto;
import jakarta.validation.constraints.Pattern;
public record CodigoBarras(@Pattern(regexp="[0-9]{8,14}|^$") String codigoBarras) {}
