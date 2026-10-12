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
$('#sair').addEventListener('click', async () => {
  try {
    const resposta = await fetchSeguro('/logout', { method: 'POST' });
    if (!resposta.ok) throw new Error('Não foi possível sair. Tente novamente.');
    location.assign('/login.html?logout');
  } catch (erro) { avisar(erro.message, true); }
});
