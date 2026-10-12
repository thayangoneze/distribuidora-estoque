package br.com.distribuidora.model;

import java.math.BigDecimal;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;

@Entity
public class Produto {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @NotBlank @Column(nullable = false) public String nome;
    @Pattern(regexp="UN|CX|PCT") public String unidadeEstoque = "UN";
    @Min(0) @Column(nullable = false) public int saldo;
    @Min(0) @Column(nullable = false) public int estoqueSeguranca;
    @Min(0) @Column(nullable = false) public int prazoEntregaDias;
    @Min(0) @Column(nullable = false) public int coberturaDesejadaDias;
    @DecimalMin("0") @Digits(integer=12, fraction=2)
    @Column(precision=14, scale=2) public BigDecimal precoCusto;
    @DecimalMin("0") @Digits(integer=12, fraction=2)
    @Column(precision=14, scale=2) public BigDecimal precoVenda;
    @Version public Long versao;
}
