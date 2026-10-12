let revisaoNota = null;
let produtosNota = [];
let notaOcupada = false;
const normalizarNomeNota = texto => texto.trim().toLocaleLowerCase('pt-BR');
function avisarNota(texto, erro = false) {
  const status = $('#status-nota');
  status.textContent = texto;
  status.hidden = false;
  status.classList.toggle('erro-nota', erro);
}
function campoNota(nome, titulo, valor, tipo = 'text', atributos = {}) {
  const label = document.createElement('label');
  const span = document.createElement('span'); span.textContent = titulo;
  const input = document.createElement('input');
  input.className = nome; input.type = tipo; input.value = valor ?? '';
  Object.entries(atributos).forEach(([chave, valor]) => input.setAttribute(chave, valor));
  label.append(span, input);
  return label;
}
function numeroNota(campo) { return campo.value.trim() === '' ? null : Number(campo.value); }
function adicionarLinhaNota(item = {}) {
  const linha = document.createElement('tr');
  const tdProduto = document.createElement('td');
  const vinculo = document.createElement('select'); vinculo.className = 'vinculo-nota';
  vinculo.setAttribute('aria-label', 'Vincular produto existente ou criar novo');
  vinculo.add(new Option('Cadastrar como produto novo', ''));
  produtosNota.forEach(p => vinculo.add(new Option(`${p.nome} (${p.unidadeEstoque || 'UN'})`, p.id)));
  tdProduto.append(vinculo, campoNota('nome-item-nota', 'Nome do produto', item.descricao, 'text', { required: '', maxlength: '255' }));
  if (item.observacao) { const obs = document.createElement('p'); obs.className = 'ajuda'; obs.textContent = item.observacao; tdProduto.append(obs); }
  const tdOriginal = document.createElement('td');
  tdOriginal.append(campoNota('quantidade-original', 'Quantidade na nota', item.quantidadeNota, 'number', { min: '0.0001', step: '0.0001' }), campoNota('unidade-original', 'Unidade na nota', item.unidadeNota, 'text', { maxlength: '32', placeholder: 'Ex.: CX ou UN' }));
  const tdEntrada = document.createElement('td');
  const unidade = document.createElement('select'); unidade.className = 'unidade-estoque-nota';
  unidade.setAttribute('aria-label', 'Unidade de estoque');
  [['UN', 'Unidade (UN)'], ['CX', 'Caixa (CX)'], ['PCT', 'Pacote (PCT)']].forEach(([valor, texto]) => unidade.add(new Option(texto, valor)));
  tdEntrada.append(campoNota('quantidade-estoque-nota', 'Quantidade de entrada', null, 'number', { required: '', min: '1', max: '100000000', step: '1' }), unidade);
  const tdPreco = document.createElement('td');
  tdPreco.append(campoNota('preco-nota', 'Preço original unitário', item.valorUnitarioNota, 'number', { min: '0', step: '0.000001', placeholder: 'Não informado' }));
  const tdParametros = document.createElement('td');
  tdParametros.append(campoNota('seguranca-nota', 'Estoque de segurança', 10, 'number', { min: '0', step: '1', required: '' }), campoNota('prazo-nota', 'Entrega (dias)', 3, 'number', { min: '0', step: '1', required: '' }), campoNota('cobertura-nota', 'Cobertura (dias)', 7, 'number', { min: '0', step: '1', required: '' }));
  const tdRemover = document.createElement('td');
  const remover = document.createElement('button'); remover.type = 'button'; remover.className = 'perigo'; remover.textContent = 'Remover';
  remover.addEventListener('click', () => { if (!notaOcupada) linha.remove(); });
  tdRemover.append(remover); linha.append(tdProduto, tdOriginal, tdEntrada, tdPreco, tdParametros, tdRemover);
  $('#itens-nota').append(linha);
  const nome = linha.querySelector('.nome-item-nota');
  const quantidadeOriginal = linha.querySelector('.quantidade-original');
  const unidadeOriginal = linha.querySelector('.unidade-original');
  const quantidade = linha.querySelector('.quantidade-estoque-nota');
  function sugerirQuantidade() {
    const unidadeLida = unidadeOriginal.value.trim().toUpperCase();
    const q = numeroNota(quantidadeOriginal);
    quantidade.value = unidadeLida === unidade.value && Number.isInteger(q) && q > 0 ? q : '';
  }
  function ajustarVinculo() {
    const existente = produtosNota.find(p => String(p.id) === vinculo.value);
    nome.readOnly = !!existente; unidade.disabled = !!existente;
    tdParametros.querySelectorAll('input').forEach(campo => campo.disabled = !!existente);
    if (existente) { nome.value = existente.nome; unidade.value = existente.unidadeEstoque || 'UN'; }
    sugerirQuantidade();
  }
  const matches = produtosNota.filter(p => normalizarNomeNota(p.nome) === normalizarNomeNota(item.descricao || ''));
  if (matches.length === 1) vinculo.value = matches[0].id;
  vinculo.addEventListener('change', ajustarVinculo);
  unidade.addEventListener('change', sugerirQuantidade);
  quantidadeOriginal.addEventListener('change', sugerirQuantidade);
  unidadeOriginal.addEventListener('change', sugerirQuantidade);
  ajustarVinculo();
}
$('#adicionar-item-nota').addEventListener('click', () => { if (!notaOcupada) adicionarLinhaNota(); });
$('#cancelar-nota').addEventListener('click', () => {
  if (notaOcupada) return;
  revisaoNota = null; $('#form-revisao-nota').hidden = true;
  avisarNota('Revisão cancelada. O estoque não foi alterado.');
});
$('#form-nota').addEventListener('submit', async evento => {
  evento.preventDefault();
  if (notaOcupada) return;
  const arquivo = $('#arquivo-nota').files[0];
  if (!arquivo || arquivo.size > 8 * 1024 * 1024) { avisarNota('Selecione um arquivo de até 8 MB.', true); return; }
  notaOcupada = true; $('#analisar-nota').disabled = true;
  $('#form-revisao-nota').hidden = true; revisaoNota = null;
  avisarNota('A IA está lendo a nota. Aguarde para conferir os itens…');
  try {
    const form = new FormData(); form.append('arquivo', arquivo);
    const revisao = await requisitar('/api/notas/analisar', { method: 'POST', body: form });
    produtosNota = await requisitar('/api/produtos');
    revisaoNota = revisao;
    $('#fornecedor-nota').value = revisao.nota.fornecedor || '';
    $('#numero-nota').value = revisao.nota.numeroNota || '';
    $('#data-nota').value = revisao.nota.dataEmissao || '';
    $('#observacoes-nota').textContent = revisao.nota.observacoes || '';
    $('#observacoes-nota').hidden = !revisao.nota.observacoes;
    $('#itens-nota').replaceChildren();
    revisao.nota.itens.forEach(adicionarLinhaNota);
    $('#revisado-nota').checked = false;
    $('#form-revisao-nota').hidden = false;
    avisarNota('Nota analisada. Confira todos os campos antes de confirmar a entrada.');
  } catch (erro) { avisarNota(erro.message, true); }
  finally { notaOcupada = false; $('#analisar-nota').disabled = false; }
});
$('#form-revisao-nota').addEventListener('submit', async evento => {
  evento.preventDefault();
  if (notaOcupada || !revisaoNota) return;
  const linhas = [...$('#itens-nota').querySelectorAll('tr')];
  if (!linhas.length) { avisarNota('Adicione ao menos um item para confirmar.', true); return; }
  const itens = linhas.map(linha => {
    const c = seletor => linha.querySelector(seletor);
    return { produtoId: c('.vinculo-nota').value ? Number(c('.vinculo-nota').value) : null,
      nome: c('.nome-item-nota').value.trim(), unidadeEstoque: c('.unidade-estoque-nota').value,
      quantidadeEstoque: Number(c('.quantidade-estoque-nota').value), quantidadeNota: numeroNota(c('.quantidade-original')),
      unidadeNota: c('.unidade-original').value.trim() || null, valorUnitarioNota: numeroNota(c('.preco-nota')),
      estoqueSeguranca: Number(c('.seguranca-nota').value), prazoEntregaDias: Number(c('.prazo-nota').value), coberturaDesejadaDias: Number(c('.cobertura-nota').value) };
  });
  const dados = { fornecedor: $('#fornecedor-nota').value.trim() || null, numeroNota: $('#numero-nota').value.trim() || null,
    dataEmissao: $('#data-nota').value || null, revisado: $('#revisado-nota').checked, itens };
  notaOcupada = true;
  const campos = [...evento.currentTarget.querySelectorAll('input, select, button')];
  const estados = campos.map(campo => campo.disabled);
  campos.forEach(campo => campo.disabled = true); $('#analisar-nota').disabled = true;
  try {
    const resposta = await requisitar(`/api/notas/${revisaoNota.importacaoId}/confirmar`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(dados)
    });
    revisaoNota = null; $('#form-revisao-nota').hidden = true; $('#form-nota').reset();
    avisarNota(`Entrada confirmada: ${resposta.itensRegistrados} itens registrados. O estoque foi atualizado.`);
    await atualizar();
  } catch (erro) { avisarNota(erro.message, true); }
  finally { notaOcupada = false; campos.forEach((campo, i) => campo.disabled = estados[i]); $('#analisar-nota').disabled = false; }
});
