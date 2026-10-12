let produtosCaixa = [];
let carrinho = new Map();
let ocupadoCaixa = false;
let envioPendente = null;
try { envioPendente = JSON.parse(sessionStorage.getItem('venda-pendente') || 'null'); } catch { sessionStorage.removeItem('venda-pendente'); }
const centavos = valor => Math.round(Number(valor) * 100);
const pagamentoNome = tipo => ({DINHEIRO:'Dinheiro',PIX:'Pix',CARTAO:'Cartão'}[tipo] || tipo);
function pendencia(dados) {
  envioPendente=dados;
  if(dados) sessionStorage.setItem('venda-pendente',JSON.stringify(dados)); else sessionStorage.removeItem('venda-pendente');
}
function bloquearCaixa() {
  const bloqueado=ocupadoCaixa || !!envioPendente;
  document.querySelectorAll('.grade-caixa input, .grade-caixa select, .grade-caixa button').forEach(c => c.disabled=bloqueado);
  $('#finalizar-venda').disabled=ocupadoCaixa || (!envioPendente && carrinho.size===0);
  $('#finalizar-venda').textContent=ocupadoCaixa?'Registrando venda…':envioPendente?'Verificar e reenviar venda':'Finalizar venda';
}
function mostrarComprovante(venda) {
  const resumo=$('#resumo-venda');resumo.replaceChildren();
  const texto=t=>{const p=document.createElement('p');p.textContent=t;resumo.append(p);};
  texto(`Venda ${venda.id} — ${new Date(venda.criadaEm).toLocaleString('pt-BR')}`);
  venda.itens.forEach(i=>texto(`${i.nome}: ${i.quantidade} ${i.unidade} × ${moeda(i.precoUnitario)} = ${moeda(i.subtotal)}`));
  texto(`Subtotal: ${moeda(venda.subtotal)} · Desconto: ${moeda(venda.desconto)} · Total: ${moeda(venda.total)}`);
  texto(`Pagamento manual: ${pagamentoNome(venda.pagamento)}`);
  if(venda.pagamento==='DINHEIRO')texto(`Recebido: ${moeda(venda.valorRecebido)} · Troco: ${moeda(venda.troco)}`);
  $('#comprovante-venda').hidden=false;
}
function concluirVenda(venda) {
  pendencia(null);carrinho.clear();$('#form-pagamento').reset();mostrarComprovante(venda);
  renderizarCarrinho();avisar(`Venda registrada. Troco: ${moeda(venda.troco)}.`);
}
function renderizarBusca() {
  const seletor=$('#produto-caixa');const selecionado=seletor.value;const busca=$('#busca-caixa').value.trim().toLocaleLowerCase('pt-BR');
  seletor.replaceChildren(new Option('Selecione um produto',''));
  produtosCaixa.filter(p=>p.nome.toLocaleLowerCase('pt-BR').includes(busca)).forEach(p=>{
    const opcao=new Option(`${p.nome} — ${moeda(p.precoVenda)} · ${p.saldo} ${p.unidadeEstoque || 'UN'}`,p.id);
    opcao.disabled=p.precoVenda==null || p.saldo<=0;seletor.add(opcao);
  });seletor.value=selecionado;
}
async function carregarCaixa() {
  try {
    const [produtos,vendas]=await Promise.all([requisitar('/api/produtos'),requisitar('/api/vendas')]);
    produtosCaixa=produtos;renderizarBusca();
    if(envioPendente) {
      const salva=vendas.find(v=>v.id===envioPendente.id);
      if(salva)concluirVenda(salva);
      else avisar('Há uma venda aguardando confirmação. Use Verificar e reenviar venda. O mesmo identificador será mantido.',true);
    }
    const tabela=$('#ultimas-vendas');tabela.replaceChildren();
    vendas.forEach(v=>{
      const tr=document.createElement('tr');celula(tr,v.id.slice(0,8));celula(tr,new Date(v.criadaEm).toLocaleString('pt-BR'));celula(tr,pagamentoNome(v.pagamento));celula(tr,moeda(v.total));
      const td=document.createElement('td');const botao=document.createElement('button');botao.type='button';botao.className='secundario';botao.textContent='Ver';botao.addEventListener('click',()=>mostrarComprovante(v));td.append(botao);tr.append(td);tabela.append(tr);
    });if(!vendas.length)vazio(tabela,5,'Nenhuma venda registrada.');
    // Preserve quantities and show current prices after an explicit refresh.
    for(const [id,item] of carrinho) {
      const atual=produtos.find(p=>p.id===id);
      if(atual) {
        if(atual.precoVenda!==item.produto.precoVenda) {$('#confirmar-pagamento').checked=false;avisar('Os preços do carrinho foram atualizados. Confira o pagamento novamente.');}
        item.produto=atual;
      }
    }
    renderizarCarrinho();
  } catch(erro){avisar(erro.message,true);}
}
function adicionarCaixa(produto,quantidade=1) {
  if(ocupadoCaixa || envioPendente)return;
  if(!produto)throw new Error('Código não encontrado. Cadastre-o na página Produtos.');
  if(produto.precoVenda==null)throw new Error(`Informe o preço de venda de ${produto.nome} na página Produtos.`);
  const atual=carrinho.get(produto.id);const total=(atual?.quantidade || 0)+quantidade;
  if(!Number.isInteger(total) || total<1 || total>1000000)throw new Error('Informe uma quantidade inteira válida.');
  if(total>produto.saldo)throw new Error(`Estoque insuficiente para ${produto.nome}. Disponível: ${produto.saldo}.`);
  if(!atual && carrinho.size>=100)throw new Error('Limite de 100 produtos por venda.');
  carrinho.set(produto.id,{produto,quantidade:total});$('#confirmar-pagamento').checked=false;renderizarCarrinho();
}
function renderizarCarrinho() {
  const tabela=$('#itens-caixa');tabela.replaceChildren();
  for(const [id,item] of carrinho) {
    const p=item.produto;const tr=document.createElement('tr');celula(tr,p.nome);celula(tr,moeda(p.precoVenda));
    const td=document.createElement('td');const qtd=document.createElement('input');qtd.type='number';qtd.className='qtd-carrinho';qtd.min='1';qtd.max=String(Math.min(p.saldo,1000000));qtd.step='1';qtd.value=item.quantidade;qtd.setAttribute('aria-label',`Quantidade de ${p.nome}`);
    qtd.addEventListener('change',()=>{
      const n=Number(qtd.value);
      if(!Number.isInteger(n) || n<1 || n>p.saldo || n>1000000){avisar('Quantidade inválida ou superior ao estoque.',true);qtd.value=item.quantidade;return;}
      item.quantidade=n;$('#confirmar-pagamento').checked=false;renderizarCarrinho();
    });td.append(qtd);tr.append(td);celula(tr,moeda((centavos(p.precoVenda)*item.quantidade)/100));
    const acoes=document.createElement('td');const remover=document.createElement('button');remover.type='button';remover.className='perigo';remover.textContent='Remover';remover.addEventListener('click',()=>{carrinho.delete(id);$('#confirmar-pagamento').checked=false;renderizarCarrinho();});acoes.append(remover);tr.append(acoes);tabela.append(tr);
  }
  if(!carrinho.size)vazio(tabela,5,'Passe um produto no leitor para iniciar a venda.');
  atualizarPagamento();bloquearCaixa();
}
function totaisCaixa() {
  const subtotal=[...carrinho.values()].reduce((s,i)=>s+centavos(i.produto.precoVenda)*i.quantidade,0);
  const desconto=centavos($('#desconto-caixa').value || 0);return {subtotal,desconto,total:subtotal-desconto};
}
function atualizarPagamento() {
  const valores=totaisCaixa();$('#subtotal-caixa').textContent=moeda(valores.subtotal/100);$('#total-caixa').textContent=moeda(valores.total/100);
  const dinheiro=$('#pagamento-caixa').value==='DINHEIRO';$('#campo-recebido').hidden=!dinheiro;$('#linha-troco').hidden=!dinheiro;$('#recebido-caixa').required=dinheiro;
  $('#troco-caixa').textContent=moeda(Math.max(0,centavos($('#recebido-caixa').value || 0)-valores.total)/100);
  $('#orientacao-pagamento').textContent=dinheiro?'Confira o dinheiro recebido antes de finalizar.':$('#pagamento-caixa').value==='PIX'?'Confirme o Pix recebido no aplicativo bancário.':'Confirme a aprovação do cartão na maquininha.';
}
$('#form-scanner').addEventListener('submit',e=>{
  e.preventDefault();const campo=$('#codigo-scanner');const codigo=campo.value.trim();
  try{if(!codigo)throw new Error('Leia ou digite um código de barras.');adicionarCaixa(produtosCaixa.find(p=>p.codigoBarras===codigo));campo.value='';}
  catch(erro){avisar(erro.message,true);campo.select();}campo.focus();
});
$('#form-busca-caixa').addEventListener('submit',e=>{
  e.preventDefault();try{adicionarCaixa(produtosCaixa.find(p=>String(p.id)===$('#produto-caixa').value),Number($('#quantidade-caixa').value));$('#quantidade-caixa').value='1';$('#codigo-scanner').focus();}
  catch(erro){avisar(erro.message,true);}
});
$('#busca-caixa').addEventListener('input',renderizarBusca);
$('#atualizar-caixa').addEventListener('click',carregarCaixa);
for(const id of ['desconto-caixa','recebido-caixa','pagamento-caixa'])$('#'+id).addEventListener('input',()=>{$('#confirmar-pagamento').checked=false;atualizarPagamento();});
$('#cancelar-venda').addEventListener('click',()=>{
  if(ocupadoCaixa || envioPendente)return;
  if(carrinho.size && !confirm('Cancelar esta venda? O estoque ainda não foi alterado.'))return;
  carrinho.clear();$('#form-pagamento').reset();renderizarCarrinho();$('#codigo-scanner').focus();
});
$('#form-pagamento').addEventListener('submit',async e=>{
  e.preventDefault();if(ocupadoCaixa)return;
  try{
    if(!envioPendente) {
      if(!carrinho.size)throw new Error('Adicione produtos antes de finalizar.');
      const t=totaisCaixa();if(!Number.isSafeInteger(t.subtotal) || t.subtotal>99999999999999)throw new Error('O total excede o limite desta versão do caixa.');if(t.desconto<0 || t.total<0)throw new Error('O desconto deve ficar entre zero e o subtotal.');
      if(!$('#confirmar-pagamento').checked)throw new Error('Confirme o recebimento do pagamento.');
      const pagamento=$('#pagamento-caixa').value;const recebido=pagamento==='DINHEIRO'?Number($('#recebido-caixa').value):null;
      if(pagamento==='DINHEIRO' && ($('#recebido-caixa').value==='' || centavos(recebido)<t.total))throw new Error('O valor recebido deve cobrir o total.');
      pendencia({id:crypto.randomUUID(),pagamento,desconto:t.desconto/100,valorRecebido:recebido,pagamentoConfirmado:true,itens:[...carrinho.values()].map(i=>({produtoId:i.produto.id,quantidade:i.quantidade,precoUnitario:i.produto.precoVenda}))});
    }
    ocupadoCaixa=true;bloquearCaixa();
    const venda=await requisitar('/api/vendas',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(envioPendente)});
    concluirVenda(venda);await carregarCaixa();
  }catch(erro){
    if(erro.status && [400,403,404,409,422].includes(erro.status))pendencia(null);
    avisar(envioPendente?`${erro.message} A venda pode ter sido registrada. Reenvie para confirmar sem repetir a baixa.`:erro.message,true);
  }finally{ocupadoCaixa=false;bloquearCaixa();if(!envioPendente)$('#codigo-scanner').focus();}
});
renderizarCarrinho();carregarCaixa();
