package br.com.distribuidora.model;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
@Entity
public class Venda {
    @Id public UUID id;
    @com.fasterxml.jackson.annotation.JsonIgnore @Column(nullable=false,length=64) public String assinatura;
    @Column(nullable=false) public Instant criadaEm;
    @Enumerated(EnumType.STRING) @Column(nullable=false) public Pagamento pagamento;
    @Column(nullable=false,precision=19,scale=2) public BigDecimal subtotal;
    @Column(nullable=false,precision=19,scale=2) public BigDecimal desconto;
    @Column(nullable=false,precision=19,scale=2) public BigDecimal total;
    @Column(precision=19,scale=2) public BigDecimal valorRecebido;
    @Column(nullable=false,precision=19,scale=2) public BigDecimal troco;
    @ElementCollection(fetch=FetchType.EAGER) @OrderColumn(name="posicao")
    public List<Item> itens=new ArrayList<>();
    public enum Pagamento { DINHEIRO, PIX, CARTAO }
    @Embeddable public static class Item {
        public Long produtoId;
        @Column(nullable=false) public String nome;
        public String codigoBarras;
        public String unidade;
        public int quantidade;
        @Column(nullable=false,precision=14,scale=2) public BigDecimal precoUnitario;
        @Column(nullable=false,precision=19,scale=2) public BigDecimal subtotal;
        public Item() {}
    }
}
