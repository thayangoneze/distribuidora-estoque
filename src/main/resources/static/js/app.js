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
      acoes.append(apagar);
      linha.append(acoes);
      lista.append(linha);
    });
    seletor.value = selecionado;
    $('#limpar-estoque').disabled = !produtos.length;
    if (!produtos.length) vazio(lista, 5, 'Nenhum produto cadastrado.');
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
