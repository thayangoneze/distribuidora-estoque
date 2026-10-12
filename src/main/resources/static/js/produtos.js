let empresasFornecedoras = [];
let produtoFornecedoresId;
function selecionarFornecedores(container, ids = []) {
  container.replaceChildren();
  if (!empresasFornecedoras.length) { const p = document.createElement('p'); p.className = 'ajuda'; p.textContent = 'Nenhuma empresa cadastrada ainda.'; container.append(p); }
  empresasFornecedoras.forEach(f => {
    const label = document.createElement('label'); const input = document.createElement('input');
    input.type = 'checkbox'; input.name = 'fornecedorIds'; input.value = f.id; input.checked = ids.includes(f.id);
    label.append(input, document.createTextNode(f.nome)); container.append(label);
  });
}
function fornecedoresSelecionados(form) { return [...form.querySelectorAll('input[name="fornecedorIds"]:checked')].map(c => Number(c.value)); }
function abrirFornecedores(p) {
  produtoFornecedoresId = p.id; $('#produto-fornecedores').textContent = p.nome;
  selecionarFornecedores($('#fornecedores-edicao'), (p.fornecedores || []).map(f => f.id));
  $('#erro-fornecedores').hidden = true; $('#dialog-fornecedores').showModal();
}
$('#cancelar-fornecedores').addEventListener('click', () => $('#dialog-fornecedores').close());
$('#form-fornecedores-produto').addEventListener('submit', async e => {
  e.preventDefault(); const botao = e.currentTarget.querySelector('button[type="submit"]'); botao.disabled = true;
  try {
    await requisitar(`/api/produtos/${produtoFornecedoresId}/fornecedores`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ fornecedorIds: fornecedoresSelecionados(e.currentTarget) }) });
    $('#dialog-fornecedores').close(); avisar('Fornecedores do produto atualizados.'); await atualizar();
  } catch (erro) { $('#erro-fornecedores').textContent = erro.message; $('#erro-fornecedores').hidden = false; }
  finally { botao.disabled = false; }
});
async function atualizar() {
  try {
    const [produtos, fornecedores] = await Promise.all([requisitar('/api/produtos'), requisitar('/api/fornecedores')]);
    empresasFornecedoras = fornecedores;
    selecionarFornecedores($('#fornecedores-novo'), fornecedoresSelecionados($('#form-produto')));
    const lista = $('#lista-produtos');
    lista.replaceChildren();
    produtos.forEach(p => {
      const linha = document.createElement('tr');
      const critico = p.saldo <= p.estoqueSeguranca;
      celula(linha, p.nome);
      celula(linha, p.codigoBarras || "Não informado");
      celula(linha, `${p.saldo} ${p.unidadeEstoque || 'UN'}`, critico ? 'critico' : '');
      celula(linha, moeda(p.precoCusto));
      celula(linha, moeda(p.precoVenda));
      celula(linha, p.estoqueSeguranca);
      celula(linha, (p.diasEntrega || []).sort((a,b) => a-b).map(d => ['Seg','Ter','Qua','Qui','Sex','Sáb','Dom'][d-1]).join(', ') || 'Não informado');
      celula(linha, (p.fornecedores || []).map(f => f.nome).sort().join(', ') || 'Não informado');
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
      const vinculos = document.createElement('button'); vinculos.type = 'button'; vinculos.className = 'secundario'; vinculos.textContent = 'Fornecedores';
      vinculos.setAttribute('aria-label', `Editar fornecedores de ${p.nome}`); vinculos.addEventListener('click', () => abrirFornecedores(p));
      const codigo = document.createElement('button'); codigo.type = 'button'; codigo.className = 'secundario'; codigo.textContent = 'Código';
      codigo.addEventListener('click', () => { produtoCodigoId = p.id; $('#produto-codigo').textContent = p.nome; $('#codigo-produto').value = p.codigoBarras || ''; $('#erro-codigo').hidden = true; $('#dialog-codigo').showModal(); $('#codigo-produto').focus(); });
      acoes.append(precos, vinculos, codigo, apagar);
      linha.append(acoes);
      lista.append(linha);
    });
    $('#limpar-estoque').disabled = !produtos.length;
    if (!produtos.length) vazio(lista, 10, 'Nenhum produto cadastrado.');
  } catch (erro) { avisar(erro.message, true); }
}
$('#form-produto').addEventListener('submit', async evento => {
  evento.preventDefault();
  const form = evento.currentTarget;
  const dados = Object.fromEntries(new FormData(form));
  for (const campo of ['estoqueSeguranca', 'saldo']) dados[campo] = Number(dados[campo]);
  Object.assign(dados, dadosPrecos(form));
  dados.fornecedorIds = fornecedoresSelecionados(form);
  delete dados.modoPreco;
  try {
    await requisitar('/api/produtos', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(dados) });
    form.reset(); configurarModoPreco(form);
    avisar('Produto cadastrado.');
    await atualizar();
  } catch (erro) { avisar(erro.message, true); }
});
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

let produtoCodigoId;
$('#cancelar-codigo').addEventListener('click', () => $('#dialog-codigo').close());
$('#form-codigo').addEventListener('submit', async e => {
  e.preventDefault(); const botao=e.currentTarget.querySelector('button[type="submit"]'); botao.disabled=true;
  try { await requisitar(`/api/produtos/${produtoCodigoId}/codigo-barras`, {method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({codigoBarras:$('#codigo-produto').value.trim() || null})}); $('#dialog-codigo').close(); avisar('Código atualizado.'); await atualizar(); }
  catch(erro) {$('#erro-codigo').textContent=erro.message;$('#erro-codigo').hidden=false;}
  finally {botao.disabled=false;}
});
