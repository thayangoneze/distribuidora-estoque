package br.com.distribuidora.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
public class ImportacaoNota {
    @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
    @Column(nullable=false,unique=true,length=64) public String hashArquivo;
    @Column(nullable=false,columnDefinition="text") public String dadosExtraidos;
    @Column(columnDefinition="text") public String dadosConfirmados;
    public String fornecedor;
    @Column(length=100) public String numeroNota;
    @Column(length=10) public String dataEmissao;
    @Column(nullable=false) public Instant criadaEm = Instant.now();
    public Instant confirmadaEm;
}
