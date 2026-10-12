async function atualizar() {
  try {
    const sugestoes = await requisitar(`/api/reposicao?dias=${$('#periodo').value}`);
    const reposicao = $('#lista-reposicao');
    reposicao.replaceChildren();
    sugestoes.forEach(s => {
      const linha = document.createElement('tr');
      celula(linha, s.nome);
      celula(linha, s.mediaDiaria.toFixed(2));
      celula(linha, s.pontoReposicao);
      celula(linha, s.quantidadeSugerida);
      selo(linha, s.baixoGiro ? 'Baixo giro' : s.estoqueCritico ? 'Estoque crítico' : 'Acompanhar',
        s.baixoGiro ? 'parado' : s.estoqueCritico ? 'alerta' : '');
      reposicao.append(linha);
    });
    if (!sugestoes.length) vazio(reposicao, 5, 'Cadastre um produto para iniciar a análise.');
  } catch (erro) { avisar(erro.message, true); }
}
$('#periodo').addEventListener('change', atualizar);
atualizar();
