const $ = (selector) => document.querySelector(selector);
const mensagem = $('#mensagem');
function avisar(texto, erro = false) {
  mensagem.textContent = texto;
  mensagem.hidden = false;
  mensagem.classList.toggle('erro', erro);
}
async function fetchSeguro(url, options = {}) {
  const headers = new Headers(options.headers);
  if (options.method && !['GET', 'HEAD'].includes(options.method)) {
    const csrfResposta = await fetch('/csrf', { cache: 'no-store' });
    if (!csrfResposta.ok) throw new Error('Não foi possível validar a sessão. Atualize a página.');
    const csrf = await csrfResposta.json();
    headers.set(csrf.headerName, csrf.token);
  }
  const resposta = await fetch(url, { ...options, headers });
  if (resposta.status === 401) {
    location.assign('/login.html');
    throw new Error('Sua sessão terminou. Entre novamente.');
  }
  return resposta;
}
async function requisitar(url, options) {
  const resposta = await fetchSeguro(url, options);
  if (!resposta.ok) {
    let detalhe = '';
    try { detalhe = (await resposta.json()).detail || ''; } catch (_) { /* resposta sem JSON */ }
    throw new Error(detalhe || `Falha na operação (HTTP ${resposta.status}).`);
  }
  return resposta.status === 204 ? null : resposta.json();
}
const moeda = valor => valor == null ? 'Não informado' : Number(valor).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
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
      celula(linha, `${p.saldo} ${p.unidadeEstoque || 'UN'}`, critico ? 'critico' : '');
      celula(linha, moeda(p.precoCusto));
      celula(linha, moeda(p.precoVenda));
      celula(linha, p.estoqueSeguranca);
      celula(linha, (p.diasEntrega || []).sort((a,b) => a-b).map(d => ['Seg','Ter','Qua','Qui','Sex','Sáb','Dom'][d-1]).join(', ') || 'Não informado');
      selo(linha, critico ? 'Estoque crítico' : 'Normal', critico ? 'alerta' : '');
      const acoes = document.createElement('td');
      const apagar = document.createElement('button');
      apagar.type = 'button';
      apagar.className = 'perigo';
      apagar.textContent = 'Apagar';
      apagar.setAttribute('aria-label', `Apagar ${p.nome}`);
      apagar.addEventListener('click', async () => {
        if (!confirm(`Apagar "${p.nome}" e todo o histórico de movimentações desse produto? Esta ação não pode ser desfeita.`)) return;
        apagar.disabled = true;
        try {
          await requisitar(`/api/produtos/${p.id}`, { method: 'DELETE' });
          avisar('Produto e histórico apagados.');
          await atualizar();
        } catch (erro) { avisar(erro.message, true); apagar.disabled = false; }
      });
      const precos = document.createElement('button');
      precos.type = 'button'; precos.className = 'secundario'; precos.textContent = 'Preços';
      precos.setAttribute('aria-label', `Editar preços de ${p.nome}`);
      precos.addEventListener('click', () => abrirPrecos(p));
      const entregas = document.createElement('button'); entregas.type = 'button'; entregas.className = 'secundario'; entregas.textContent = 'Entregas';
      entregas.addEventListener('click', () => abrirEntregas(p));
      acoes.append(precos, entregas, apagar);
      linha.append(acoes);
      lista.append(linha);
    });
    seletor.value = selecionado;
    $('#limpar-estoque').disabled = !produtos.length;
    if (!produtos.length) vazio(lista, 8, 'Nenhum produto cadastrado.');
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
  for (const campo of ['estoqueSeguranca', 'saldo']) dados[campo] = Number(dados[campo]);
  Object.assign(dados, dadosPrecos(form));
  dados.diasEntrega = [...form.querySelectorAll('input[name="diasEntrega"]:checked')].map(c => Number(c.value));
  delete dados.modoPreco;
  try {
    await requisitar('/api/produtos', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(dados) });
    form.reset(); configurarModoPreco(form);
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
const dialogLimpeza = $('#dialog-limpeza');
$('#limpar-estoque').addEventListener('click', () => {
  $('#form-limpeza').reset();
  $('#erro-limpeza').hidden = true;
  dialogLimpeza.showModal();
  $('#senha-administrador').focus();
});
$('#cancelar-limpeza').addEventListener('click', () => dialogLimpeza.close());
dialogLimpeza.addEventListener('close', () => $('#form-limpeza').reset());
$('#form-limpeza').addEventListener('submit', async evento => {
  evento.preventDefault();
  const botao = evento.currentTarget.querySelector('button[type="submit"]');
  botao.disabled = true;
  try {
    const resposta = await fetchSeguro('/api/produtos', {
      method: 'DELETE', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ senha: $('#senha-administrador').value })
    });
    if (resposta.status === 403) throw new Error('Senha do administrador incorreta.');
    if (!resposta.ok) throw new Error(`Não foi possível limpar o estoque (HTTP ${resposta.status}).`);
    dialogLimpeza.close();
    avisar('Todos os produtos e históricos foram apagados.');
    await atualizar();
  } catch (erro) {
    $('#erro-limpeza').textContent = erro.message;
    $('#erro-limpeza').hidden = false;
  } finally { botao.disabled = false; }
});
function configurarModoPreco(form) {
  const automatico = form.elements.modoPreco.value === 'percentual';
  form.querySelector('.campo-percentual').hidden = !automatico;
  form.querySelector('.ajuda-percentual').hidden = !automatico;
  form.elements.percentualAcrescimo.required = automatico;
  form.elements.precoCusto.required = automatico;
  form.elements.precoVenda.readOnly = automatico;
  if (automatico) {
    const custo = form.elements.precoCusto.value;
    const percentual = form.elements.percentualAcrescimo.value;
    form.elements.precoVenda.value = custo !== '' && percentual !== ''
      ? (Math.floor(Number(custo) * (1 + Number(percentual) / 100) * 2 + 0.5 + 1e-9) / 2).toFixed(2) : '';
  }
}
function dadosPrecos(form) {
  const numero = nome => form.elements[nome].value === '' ? null : Number(form.elements[nome].value);
  return { precoCusto: numero('precoCusto'), precoVenda: numero('precoVenda'),
    percentualAcrescimo: form.elements.modoPreco.value === 'percentual' ? numero('percentualAcrescimo') : null,
    calcularPrecoVenda: form.elements.modoPreco.value === 'percentual' };
}
for (const form of [$('#form-produto'), $('#form-precos')]) {
  for (const nome of ['modoPreco', 'precoCusto', 'percentualAcrescimo']) form.elements[nome].addEventListener('input', () => configurarModoPreco(form));
  configurarModoPreco(form);
}
let produtoEntregasId;
function abrirEntregas(p) {
  produtoEntregasId = p.id; $('#produto-entregas').textContent = p.nome;
  $('#form-entregas').querySelectorAll('input[name="diasEntrega"]').forEach(c => c.checked = (p.diasEntrega || []).includes(Number(c.value)));
  $('#erro-entregas').hidden = true; $('#dialog-entregas').showModal();
}
$('#cancelar-entregas').addEventListener('click', () => $('#dialog-entregas').close());
$('#form-entregas').addEventListener('submit', async e => {
  e.preventDefault(); const botao = e.currentTarget.querySelector('button[type="submit"]'); botao.disabled = true;
  const diasEntrega = [...e.currentTarget.querySelectorAll('input[name="diasEntrega"]:checked')].map(c => Number(c.value));
  try {
    await requisitar(`/api/produtos/${produtoEntregasId}/entregas`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ diasEntrega }) });
    $('#dialog-entregas').close(); avisar('Dias de entrega atualizados.'); await atualizar();
  } catch (erro) { $('#erro-entregas').textContent = erro.message; $('#erro-entregas').hidden = false; }
  finally { botao.disabled = false; }
});
const dialogPrecos = $('#dialog-precos');
let produtoPrecosId;
function abrirPrecos(produto) {
  produtoPrecosId = produto.id;
  $('#produto-precos').textContent = `${produto.nome} — preços por ${produto.unidadeEstoque || 'UN'}`;
  const form = $('#form-precos');
  form.elements.precoCusto.value = produto.precoCusto ?? '';
  form.elements.precoVenda.value = produto.precoVenda ?? '';
  form.elements.percentualAcrescimo.value = produto.percentualAcrescimo ?? '';
  form.elements.modoPreco.value = produto.percentualAcrescimo == null ? 'manual' : 'percentual';
  configurarModoPreco(form);
  $('#erro-precos').hidden = true;
  dialogPrecos.showModal(); form.elements.precoCusto.focus();
}
$('#cancelar-precos').addEventListener('click', () => dialogPrecos.close());
$('#form-precos').addEventListener('submit', async evento => {
  evento.preventDefault();
  const form = evento.currentTarget;
  const botao = form.querySelector('button[type="submit"]'); botao.disabled = true;
  const dados = dadosPrecos(form);
  try {
    await requisitar(`/api/produtos/${produtoPrecosId}/precos`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(dados) });
    dialogPrecos.close(); avisar('Preços atualizados.'); await atualizar();
  } catch (erro) { $('#erro-precos').textContent = erro.message; $('#erro-precos').hidden = false; }
  finally { botao.disabled = false; }
});
atualizar();

const linksNavegacao = document.querySelectorAll('.nav-link');
$('#sair').addEventListener('click', async () => {
  try {
    const resposta = await fetchSeguro('/logout', { method: 'POST' });
    if (!resposta.ok) throw new Error('Não foi possível sair. Tente novamente.');
    location.assign('/login.html?logout');
  } catch (erro) { avisar(erro.message, true); }
});
linksNavegacao.forEach(link => link.addEventListener('click', () => {
  linksNavegacao.forEach(item => {
    item.classList.toggle('ativo', item === link);
    if (item === link) item.setAttribute('aria-current', 'location');
    else item.removeAttribute('aria-current');
  });
}));
