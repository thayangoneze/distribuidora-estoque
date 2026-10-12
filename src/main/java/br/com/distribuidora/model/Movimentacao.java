package br.com.distribuidora.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.math.BigDecimal;

@Entity
public class Movimentacao {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @ManyToOne(optional = false) public Produto produto;
    @Enumerated(EnumType.STRING) @Column(nullable = false) public Tipo tipo;
    @Column(nullable = false) public int quantidade;
    @Column(nullable = false) public Instant criadaEm;
    @ManyToOne public ImportacaoNota importacaoNota;
    @Column(precision=19,scale=6) public BigDecimal valorUnitarioNota;
    @Column(precision=14,scale=4) public BigDecimal quantidadeNota;
    @Column(length=32) public String unidadeNota;
    public enum Tipo { ENTRADA, SAIDA }
    protected Movimentacao() {}
    public Movimentacao(Produto produto, Tipo tipo, int quantidade) {
        this.produto = produto; this.tipo = tipo; this.quantidade = quantidade; this.criadaEm = Instant.now();
    }
}
