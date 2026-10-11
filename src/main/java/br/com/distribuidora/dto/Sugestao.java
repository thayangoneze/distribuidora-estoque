package br.com.distribuidora.dto;

public record Sugestao(Long produtoId, String nome, int saldo, int estoqueSeguranca,
                       double mediaDiaria, int pontoReposicao, int quantidadeSugerida,
                       boolean baixoGiro, boolean estoqueCritico) {}
