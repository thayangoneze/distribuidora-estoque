async function atualizar() {
  try {
    const sugestoes = await requisitar(`/api/reposicao?dias=${$('#periodo').value}`);
    const criticos = sugestoes.filter(s => s.saldo === 0 || s.saldo <= s.estoqueSeguranca)
      .sort((a, b) => Number(b.saldo === 0) - Number(a.saldo === 0));
    const reposicao = $('#lista-reposicao');
    reposicao.replaceChildren();
    criticos.forEach(s => {
      const linha = document.createElement('tr');
      celula(linha, s.nome);
      celula(linha, s.saldo);
      celula(linha, s.mediaDiaria.toFixed(2));
      celula(linha, s.pontoReposicao);
      celula(linha, s.quantidadeSugerida);
      const situacao = s.saldo === 0 ? 'Sem estoque' : 'Estoque crítico';
      selo(linha, situacao + (s.baixoGiro ? ' · Baixo giro' : ''), 'alerta');
      reposicao.append(linha);
    });
    if (!criticos.length) vazio(reposicao, 6, 'Nenhum produto está sem estoque ou em estado crítico.');
  } catch (erro) { avisar(erro.message, true); }
}
$('#periodo').addEventListener('change', atualizar);
atualizar();
