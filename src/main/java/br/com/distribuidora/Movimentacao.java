package br.com.distribuidora;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class Movimentacao {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @ManyToOne(optional = false) public Produto produto;
    @Enumerated(EnumType.STRING) @Column(nullable = false) public Tipo tipo;
    @Column(nullable = false) public int quantidade;
    @Column(nullable = false) public Instant criadaEm;
    public enum Tipo { ENTRADA, SAIDA }
    protected Movimentacao() {}
    public Movimentacao(Produto produto, Tipo tipo, int quantidade) {
        this.produto = produto; this.tipo = tipo; this.quantidade = quantidade; this.criadaEm = Instant.now();
    }
}
