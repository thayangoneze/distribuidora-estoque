let conexaoCelular=null;
let intervaloCelular=null;
let consultandoCelular=false;
const leiturasAplicadas=new Map();
let imagemQrUrl=null;
async function encerrarCelular() {
  const id=conexaoCelular;conexaoCelular=null;clearInterval(intervaloCelular);intervaloCelular=null;leiturasAplicadas.clear();
  $('#estado-celular').textContent='Nenhum celular conectado.';$('#qr-celular').hidden=true;
  if(imagemQrUrl){URL.revokeObjectURL(imagemQrUrl);imagemQrUrl=null;}
  if(id)try{await requisitar(`/api/leitores/${id}`,{method:'DELETE'});}catch{ /* Canal expira no servidor mesmo se a conexão falhar. */ }
}
async function consultarCelular() {
  if(!conexaoCelular || consultandoCelular)return;
  const id=conexaoCelular;consultandoCelular=true;
  try {
    const estado=await requisitar(`/api/leitores/${id}`);
    if(conexaoCelular!==id)return;
    $('#estado-celular').textContent=estado.conectado?'Celular conectado. Pode ler os produtos.':'Aguardando o celular abrir o QR Code e entrar no sistema.';
    const leituras=await requisitar(`/api/leitores/${id}/leituras`);
    for(const leitura of leituras) {
      if(conexaoCelular!==id)return;
      let resultado=leiturasAplicadas.get(leitura.id);
      if(!resultado) {
        if(ocupadoCaixa || envioPendente)resultado={aceito:false,mensagem:'Caixa em pagamento. Aguarde e leia novamente.'};
        else {
          try {
            let produto=produtosCaixa.find(p=>p.codigoBarras===leitura.codigo);
            if(!produto){produtosCaixa=await requisitar('/api/produtos');produto=produtosCaixa.find(p=>p.codigoBarras===leitura.codigo);}
            if(conexaoCelular!==id)return;
            if(ocupadoCaixa || envioPendente)throw new Error('Caixa em pagamento. Aguarde e leia novamente.');
            adicionarCaixa(produto);resultado={aceito:true,mensagem:`${produto.nome}: 1 ${produto.unidadeEstoque || 'UN'} adicionado ao caixa.`};
          }catch(e){resultado={aceito:false,mensagem:e.message.slice(0,200)};}
        }
        leiturasAplicadas.set(leitura.id,resultado);
      }
      await requisitar(`/api/leitores/${id}/leituras/${leitura.id}/confirmar`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(resultado)});
    }
  }catch(e){
    if(e.status===410 || e.status===403) {await encerrarCelular();$('#estado-celular').textContent=e.message;}
    else $('#estado-celular').textContent='Falha na conexão com o celular. Tentando novamente…';
  }finally{consultandoCelular=false;}
}
$('#conectar-celular').addEventListener('click',()=>{
  $('#origem-celular').value=location.protocol==='https:'?location.origin:$('#origem-celular').value;
  $('#erro-celular').hidden=true;$('#dialog-celular').showModal();
});
$('#fechar-celular').addEventListener('click',()=>$('#dialog-celular').close());
$('#desconectar-celular').addEventListener('click',encerrarCelular);
$('#form-conexao-celular').addEventListener('submit',async e=>{
  e.preventDefault();const botao=e.currentTarget.querySelector('button[type="submit"]');botao.disabled=true;
  try {
    const origem=new URL($('#origem-celular').value.trim());
    if(origem.protocol!=='https:' || origem.username || origem.password || origem.search || origem.hash || (origem.pathname!=='/'))throw new Error('Informe somente o endereço base HTTPS do sistema.');
    await encerrarCelular();const canal=await requisitar('/api/leitores',{method:'POST'});conexaoCelular=canal.id;
    const response=await fetchSeguro(`/api/leitores/${canal.id}/qr?origem=${encodeURIComponent(origem.origin)}`);
    if(!response.ok)throw new Error('Não foi possível gerar o QR Code.');
    imagemQrUrl=URL.createObjectURL(await response.blob());$('#imagem-qr').src=imagemQrUrl;
    $('#link-leitor').href=`${origem.origin}/leitor.html?conexao=${canal.id}`;$('#qr-celular').hidden=false;
    intervaloCelular=setInterval(consultarCelular,1000);consultarCelular();
  }catch(e){$('#erro-celular').textContent=e.message;$('#erro-celular').hidden=false;await encerrarCelular();}
  finally{botao.disabled=false;}
});
