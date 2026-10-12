async function atualizar() {
  try {
    const [produtos, sugestoes] = await Promise.all([requisitar('/api/produtos'), requisitar('/api/reposicao?dias=30')]);
    $('#total-produtos').textContent = produtos.length;
    $('#total-criticos').textContent = produtos.filter(p => p.saldo <= p.estoqueSeguranca).length;
    $('#total-sugestoes').textContent = sugestoes.filter(s => s.quantidadeSugerida > 0).length;
  } catch (erro) { avisar(erro.message, true); }
}
atualizar();
