package br.com.distribuidora.model;

import java.math.BigDecimal;
import java.util.Set;
import java.util.HashSet;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;

@Entity
public class Produto {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @NotBlank @Column(nullable = false) public String nome;
    @Pattern(regexp="UN|CX|PCT") public String unidadeEstoque = "UN";
    @Min(0) @Column(nullable = false) public int saldo;
    @Min(0) @Column(nullable = false) public int estoqueSeguranca;
    @JsonIgnore @Column(nullable = false) public int prazoEntregaDias;
    @JsonIgnore @Column(nullable = false) public int coberturaDesejadaDias;
    @ElementCollection(fetch = FetchType.EAGER)
    public Set<@NotNull @Min(1) @Max(7) Integer> diasEntrega = new HashSet<>();
    @DecimalMin("0") @DecimalMax("100000") @Digits(integer=6,fraction=2)
    public BigDecimal percentualAcrescimo;
    @Transient public boolean calcularPrecoVenda;
    @DecimalMin("0") @Digits(integer=12, fraction=2)
    @Column(precision=14, scale=2) public BigDecimal precoCusto;
    @DecimalMin("0") @Digits(integer=12, fraction=2)
    @Column(precision=14, scale=2) public BigDecimal precoVenda;
    @Version public Long versao;
}
