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
    @com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    public Set<Integer> getDiasEntrega() {
        Set<Integer> dias = new java.util.TreeSet<>();
        for (Fornecedor f : fornecedores) dias.addAll(f.diasEntrega);
        return dias;
    }
    @DecimalMin("0") @DecimalMax("100000") @Digits(integer=6,fraction=2)
    public BigDecimal percentualAcrescimo;
    @Transient public boolean calcularPrecoVenda;
    @DecimalMin("0") @Digits(integer=12, fraction=2)
    @Column(precision=14, scale=2) public BigDecimal precoCusto;
    @DecimalMin("0") @Digits(integer=12, fraction=2)
    @Column(precision=14, scale=2) public BigDecimal precoVenda;
    @ManyToMany(fetch=FetchType.EAGER)
    @JoinTable(name="produto_fornecedor", joinColumns=@JoinColumn(name="produto_id"), inverseJoinColumns=@JoinColumn(name="fornecedor_id"))
    @com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    public Set<Fornecedor> fornecedores = new HashSet<>();
    @Transient @Size(max=100) public Set<@NotNull @Positive Long> fornecedorIds = new HashSet<>();
    @Version public Long versao;
}
