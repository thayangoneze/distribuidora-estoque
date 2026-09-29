package br.com.distribuidora;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

@Entity
public class Produto {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @NotBlank @Column(nullable = false) public String nome;
    @Min(0) @Column(nullable = false) public int saldo;
    @Min(0) @Column(nullable = false) public int estoqueSeguranca;
    @Min(0) @Column(nullable = false) public int prazoEntregaDias;
    @Min(0) @Column(nullable = false) public int coberturaDesejadaDias;
    @Version public Long versao;
}
