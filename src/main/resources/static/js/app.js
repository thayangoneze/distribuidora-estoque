const $ = (selector) => document.querySelector(selector);
const mensagem = $('#mensagem');
function avisar(texto, erro = false) {
  mensagem.textContent = texto;
  mensagem.hidden = false;
  mensagem.classList.toggle('erro', erro);
}
async function requisitar(url, options) {
  const resposta = await fetch(url, options);
  if (!resposta.ok) {
    let detalhe = '';
    try { detalhe = (await resposta.json()).detail || ''; } catch (_) { /* resposta sem JSON */ }
    throw new Error(detalhe || `Falha na operação (HTTP ${resposta.status}).`);
  }
  return resposta.json();
}
function celula(linha, valor, classe) {
  const td = document.createElement('td');
  td.textContent = valor;
  if (classe) td.className = classe;
  linha.append(td);
}
function selo(linha, texto, classe) {
  const td = document.createElement('td');
  const span = document.createElement('span');
  span.className = `selo ${classe || ''}`;
  span.textContent = texto;
  td.append(span);
  linha.append(td);
}
function vazio(tbody, colunas, texto) {
  const linha = tbody.insertRow();
  const td = linha.insertCell();
  td.colSpan = colunas;
  td.textContent = texto;
}
async function atualizar() {
  try {
    const [produtos, sugestoes] = await Promise.all([
      requisitar('/api/produtos'), requisitar(`/api/reposicao?dias=${$('#periodo').value}`)
    ]);
    $('#total-produtos').textContent = produtos.length;
    $('#total-criticos').textContent = produtos.filter(p => p.saldo <= p.estoqueSeguranca).length;
    $('#total-sugestoes').textContent = sugestoes.filter(s => s.quantidadeSugerida > 0).length;
    const seletor = $('#produto-id');
    const selecionado = seletor.value;
    seletor.replaceChildren(new Option('Selecione um produto', ''));
    const lista = $('#lista-produtos');
    lista.replaceChildren();
    produtos.forEach(p => {
      seletor.add(new Option(p.nome, p.id));
      const linha = document.createElement('tr');
      const critico = p.saldo <= p.estoqueSeguranca;
      celula(linha, p.nome);
      celula(linha, p.saldo, critico ? 'critico' : '');
      celula(linha, p.estoqueSeguranca);
      selo(linha, critico ? 'Estoque crítico' : 'Normal', critico ? 'alerta' : '');
      lista.append(linha);
    });
    seletor.value = selecionado;
    if (!produtos.length) vazio(lista, 4, 'Nenhum produto cadastrado.');
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
$('#form-produto').addEventListener('submit', async evento => {
  evento.preventDefault();
  const form = evento.currentTarget;
  const dados = Object.fromEntries(new FormData(form));
  for (const campo of ['estoqueSeguranca', 'prazoEntregaDias', 'coberturaDesejadaDias']) dados[campo] = Number(dados[campo]);
  try {
    await requisitar('/api/produtos', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(dados) });
    form.reset();
    avisar('Produto cadastrado.');
    await atualizar();
  } catch (erro) { avisar(erro.message, true); }
});
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
$('#periodo').addEventListener('change', atualizar);
$('#atualizar').addEventListener('click', atualizar);
atualizar();
