const cabecalhoLote='produto;unidade;quantidade;custo;venda;acrescimo;seguranca;fornecedores';
let previaLote=null;
let loteOcupado=false;
let pedidoLote=null;
const normalizarLote=s=>s.trim().replace(/\s+/g,' ').toLocaleLowerCase('pt-BR');
function statusLote(texto,erro=false){$('#status-lote').textContent=texto;$('#status-lote').hidden=false;$('#status-lote').classList.toggle('critico',erro);}
function bloquearLote(bloqueado){document.querySelectorAll('#cadastro-lote input, #cadastro-lote textarea, #cadastro-lote button').forEach(c=>c.disabled=bloqueado);}
function invalidarLote(){previaLote=null;pedidoLote=null;$('#form-lote').hidden=true;$('#confirmar-lote').checked=false;}
function numeroLote(texto,campo,linha,inteiro=false,opcional=false) {
  const valor=texto.trim();if(opcional && !valor)return null;
  if(!/^\d+(?:[.,]\d{1,2})?$/.test(valor) || (inteiro && !/^\d+$/.test(valor)))throw new Error(`Linha ${linha}: ${campo} inválido. Use valores positivos, sem separador de milhar.`);
  const n=Number(valor.replace(',','.'));const max=inteiro?2147483647:999999999999.99;
  if(!Number.isFinite(n) || n>max)throw new Error(`Linha ${linha}: ${campo} excede o limite.`);return n;
}
function lerLote(texto) {
  const linhas=texto.replace(/^\uFEFF/,'').trim().split(/\r?\n/).filter(l=>l.trim());
  if(linhas[0]?.trim().toLowerCase()!==cabecalhoLote)throw new Error('Use o cabeçalho indicado no formato. O botão dos exemplos já o preenche.');
  if(linhas.length<2 || linhas.length>201)throw new Error('Informe entre 1 e 200 produtos.');
  const nomes=new Set();return linhas.slice(1).map((linha,i)=>{
    const campos=linha.split(';').map(c=>c.trim());if(campos.length!==8)throw new Error(`Linha ${i+2}: são necessárias 8 colunas separadas por ponto e vírgula.`);
    const [nome,unidade,quantidade,custo,venda,acrescimo,seguranca,empresas]=campos;
    if(!nome || nome.length>255 || nomes.has(normalizarLote(nome)))throw new Error(`Linha ${i+2}: nome vazio, muito longo ou repetido.`);nomes.add(normalizarLote(nome));
    if(!['UN','CX','PCT'].includes(unidade.toUpperCase()))throw new Error(`Linha ${i+2}: use UN, CX ou PCT.`);
    const p={nome,unidadeEstoque:unidade.toUpperCase(),saldo:numeroLote(quantidade,'quantidade',i+2,true),estoqueSeguranca:numeroLote(seguranca || '0','segurança',i+2,true),precoCusto:numeroLote(custo,'custo',i+2,false,true),precoVenda:numeroLote(venda,'venda',i+2,false,true),percentualAcrescimo:numeroLote(acrescimo,'acréscimo',i+2,false,true),calcularPrecoVenda:acrescimo!=='',fornecedores:empresas?empresas.split('|').map(e=>e.trim()):[]};
    if(p.fornecedores.some(e=>!e || e.length>255) || p.fornecedores.length>20)throw new Error(`Linha ${i+2}: confira os fornecedores.`);
    if(p.calcularPrecoVenda && (p.precoCusto==null || p.precoVenda!=null || p.percentualAcrescimo>100000))throw new Error(`Linha ${i+2}: para acréscimo, informe custo e deixe venda em branco (limite 100000%).`);
    return p;
  });
}
$('#texto-lote').addEventListener('input',invalidarLote);
$('#criar-empresas-lote').addEventListener('change',()=>pedidoLote=null);
$('#carregar-exemplos').addEventListener('click',async()=>{
  if(loteOcupado)return;loteOcupado=true;bloquearLote(true);
  try{const exemplos=await requisitar('/dados/produtos-exemplo.json');$('#texto-lote').value=cabecalhoLote+'\n'+exemplos.map(p=>[p.nome,p.unidadeEstoque,p.saldo,p.precoCusto,p.precoVenda,'',p.estoqueSeguranca,p.fornecedores.join('|')].join(';')).join('\n');invalidarLote();statusLote('50 exemplos carregados. Revise os dados antes de cadastrar.');}
  catch(e){statusLote(e.message,true);}
  finally{loteOcupado=false;bloquearLote(false);}
});
$('#revisar-lote').addEventListener('click',async()=>{
  if(loteOcupado)return;invalidarLote();loteOcupado=true;bloquearLote(true);
  try {
    const itens=lerLote($('#texto-lote').value);const [produtos,empresas]=await Promise.all([requisitar('/api/produtos'),requisitar('/api/fornecedores')]);
    const existentes=new Set(produtos.map(p=>normalizarLote(p.nome)));const repetidos=itens.filter(p=>existentes.has(normalizarLote(p.nome)));
    if(repetidos.length)throw new Error(`Já cadastrados: ${repetidos.map(p=>p.nome).join(', ')}. Retire essas linhas para não duplicar.`);
    const lista=$('#previa-lote');lista.replaceChildren();itens.forEach(p=>{const linha=document.createElement('tr');const venda=p.calcularPrecoVenda?Math.floor(p.precoCusto*(1+p.percentualAcrescimo/100)*2+0.5+1e-9)/2:p.precoVenda;for(const v of [p.nome,p.unidadeEstoque,p.saldo,moeda(p.precoCusto),moeda(venda),p.estoqueSeguranca,p.fornecedores.join(', ') || 'Não informado'])celula(linha,v);lista.append(linha);});
    const nomesEmpresas=new Set(empresas.map(f=>normalizarLote(f.nome)));const novas=[...new Set(itens.flatMap(p=>p.fornecedores))].filter(n=>!nomesEmpresas.has(normalizarLote(n)));
    $('#empresas-lote').textContent=novas.length?`Empresas novas que serão cadastradas se autorizado: ${novas.join(', ')}.`:'Os fornecedores indicados já estão cadastrados.';
    previaLote=itens;$('#form-lote').hidden=false;statusLote(`${itens.length} produtos prontos para conferência. Nenhum cadastro foi feito ainda.`);
  }catch(e){statusLote(e.message,true);}
  finally{loteOcupado=false;bloquearLote(false);}
});
$('#cancelar-lote').addEventListener('click',()=>{if(!loteOcupado){invalidarLote();statusLote('Revisão cancelada. Nenhum cadastro foi enviado.');}});
$('#form-lote').addEventListener('submit',async e=>{
  e.preventDefault();if(loteOcupado || !previaLote)return;
  if(!$('#confirmar-lote').checked){statusLote('Marque a confirmação após revisar os dados.',true);return;}
  if(!pedidoLote)pedidoLote={id:crypto.randomUUID(),revisado:true,criarFornecedores:$('#criar-empresas-lote').checked,itens:previaLote};
  loteOcupado=true;bloquearLote(true);
  try{
    const resultado=await requisitar('/api/produtos/lote',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(pedidoLote)});
    invalidarLote();$('#texto-lote').value='';statusLote(`${resultado.produtosCriados} produtos e ${resultado.fornecedoresCriados} empresas cadastrados. Estoque inicial registrado.`);await atualizar();
  }catch(e){
    if(e.status && e.status<500)pedidoLote=null;
    statusLote(e.status?e.message:`${e.message} Reenvie a mesma revisão para verificar sem repetir o cadastro.`,true);
  }finally{loteOcupado=false;bloquearLote(false);}
});
