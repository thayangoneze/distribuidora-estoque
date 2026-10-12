package br.com.distribuidora.model;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
@Entity
public class Fornecedor {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(nullable=false, length=255) public String nome;
    @JsonIgnore @Column(nullable=false, unique=true, length=255) public String nomeNormalizado;
}
