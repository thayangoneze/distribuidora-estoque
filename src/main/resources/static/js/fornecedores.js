$('#form-fornecedor').addEventListener('submit', async e => {
  e.preventDefault(); const form = e.currentTarget; const botao = form.querySelector('button[type="submit"]'); botao.disabled = true;
  try {
    await requisitar('/api/fornecedores', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ nome: form.elements.nome.value.trim(), diasEntrega: [...form.querySelectorAll('input[name="diasEntrega"]:checked')].map(c => Number(c.value)) }) });
    form.reset(); $('#status-fornecedor').textContent = 'Empresa cadastrada. Agora selecione-a no cadastro ou no botão Fornecedores do produto.'; $('#status-fornecedor').hidden = false;
    await atualizar();
  } catch (erro) { $('#status-fornecedor').textContent = erro.message; $('#status-fornecedor').hidden = false; }
  finally { botao.disabled = false; }
});
async function atualizar() {
  try {
    const [produtos, fornecedores] = await Promise.all([requisitar('/api/produtos'), requisitar('/api/fornecedores')]);
    const tabelaEmpresas = $('#lista-fornecedores'); tabelaEmpresas.replaceChildren();
    fornecedores.forEach(f => {
      const linha = document.createElement('tr'); celula(linha, f.nome);
      celula(linha, (f.diasEntrega || []).sort((a,b) => a-b).map(d => ['Seg','Ter','Qua','Qui','Sex','Sáb','Dom'][d-1]).join(', ') || 'Não informado');
      celula(linha, produtos.filter(p => (p.fornecedores || []).some(v => v.id === f.id)).map(p => p.nome).join(', ') || 'Nenhum produto vinculado');
      const acoes = document.createElement('td'); const editar = document.createElement('button'); editar.type = 'button'; editar.className = 'secundario'; editar.textContent = 'Entregas';
      editar.setAttribute('aria-label', `Editar dias de entrega de ${f.nome}`); editar.addEventListener('click', () => abrirEntregas(f)); acoes.append(editar); linha.append(acoes);
      tabelaEmpresas.append(linha);
    });
    if (!fornecedores.length) vazio(tabelaEmpresas, 4, 'Cadastre a primeira empresa fornecedora.');
  } catch (erro) { avisar(erro.message, true); }
}
let empresaEntregasId;
function abrirEntregas(p) {
  empresaEntregasId = p.id; $('#produto-entregas').textContent = p.nome;
  $('#form-entregas').querySelectorAll('input[name="diasEntrega"]').forEach(c => c.checked = (p.diasEntrega || []).includes(Number(c.value)));
  $('#erro-entregas').hidden = true; $('#dialog-entregas').showModal();
}
$('#cancelar-entregas').addEventListener('click', () => $('#dialog-entregas').close());
$('#form-entregas').addEventListener('submit', async e => {
  e.preventDefault(); const botao = e.currentTarget.querySelector('button[type="submit"]'); botao.disabled = true;
  const diasEntrega = [...e.currentTarget.querySelectorAll('input[name="diasEntrega"]:checked')].map(c => Number(c.value));
  try {
    await requisitar(`/api/fornecedores/${empresaEntregasId}/entregas`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ diasEntrega }) });
    $('#dialog-entregas').close(); avisar('Dias de entrega atualizados.'); await atualizar();
  } catch (erro) { $('#erro-entregas').textContent = erro.message; $('#erro-entregas').hidden = false; }
  finally { botao.disabled = false; }
});
atualizar();
