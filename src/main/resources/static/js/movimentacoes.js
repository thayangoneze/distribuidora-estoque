async function atualizar() {
  try {
    const produtos = await requisitar('/api/produtos');
    const seletor = $('#produto-id'); const selecionado = seletor.value;
    seletor.replaceChildren(new Option('Selecione um produto', ''));
    produtos.forEach(p => seletor.add(new Option(`${p.nome} — ${p.saldo} ${p.unidadeEstoque || 'UN'}`, p.id)));
    seletor.value = selecionado;
    if (!produtos.length) avisar('Cadastre um produto na página Produtos para registrar movimentações.');
  } catch (erro) { avisar(erro.message, true); }
}
$('#form-movimentacao').addEventListener('submit', async evento => {
  evento.preventDefault();
  const form = evento.currentTarget;
  const dados = Object.fromEntries(new FormData(form));
  const id = encodeURIComponent(dados.produtoId);
  try {
    await requisitar(`/api/produtos/${id}/movimentacoes`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ tipo: dados.tipo, quantidade: Number(dados.quantidade) })
    });
    avisar('Movimentação registrada.');
    await atualizar();
    $('#produto-id').value = dados.produtoId;
  } catch (erro) { avisar(erro.message, true); }
});
atualizar();
