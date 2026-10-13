package br.com.distribuidora.model;
import jakarta.persistence.*;
import java.util.UUID;
import java.time.Instant;
@Entity
public class RegistroCadastroLote {
    @Id public UUID id;
    @Column(nullable=false,length=64) public String assinatura;
    @Column(nullable=false) public int produtosCriados;
    @Column(nullable=false) public int fornecedoresCriados;
    @Column(nullable=false) public Instant criadoEm;
}
