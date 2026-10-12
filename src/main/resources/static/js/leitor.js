const canalLeitor=new URLSearchParams(location.search).get('conexao');
let controlesCamera=null;
let geracaoCamera=0;
let conectadoLeitor=false;
let envioLeitor=null;
let aguardandoLeitor=false;
function statusLeitor(texto){$('#status-leitor').textContent=texto;}
function pararCamera() {geracaoCamera++;if(controlesCamera){controlesCamera.stop();controlesCamera=null;}const video=$('#camera-leitor');if(video.srcObject)video.srcObject.getTracks().forEach(t=>t.stop());video.srcObject=null;}
function botoesLeitor(){ $('#iniciar-camera').disabled=!conectadoLeitor || aguardandoLeitor;$('#enviar-codigo-manual').disabled=!conectadoLeitor || aguardandoLeitor; }
async function aguardarResultado() {
  if(!envioLeitor)return;
  try {
    const resultado=await requisitar(`/api/leitores/${canalLeitor}/leituras/${envioLeitor.id}`);
    if(resultado.status==='PENDENTE'){statusLeitor('Código enviado. Aguardando o caixa no computador…');setTimeout(aguardarResultado,1000);return;}
    statusLeitor(resultado.mensagem);envioLeitor=null;aguardandoLeitor=false;botoesLeitor();
  }catch(e){
    statusLeitor(e.message);
    if([403,410].includes(e.status)){conectadoLeitor=false;aguardandoLeitor=false;envioLeitor=null;botoesLeitor();}
    else setTimeout(aguardarResultado,2000);
  }
}
async function enviarCodigo(codigo) {
  if(aguardandoLeitor || !conectadoLeitor)return;
  pararCamera();
  if(!/^[0-9]{8,14}$/.test(codigo)){statusLeitor('Leia um código de barras numérico de 8 a 14 dígitos.');return;}
  aguardandoLeitor=true;botoesLeitor();envioLeitor={id:crypto.randomUUID(),codigo};
  await reenviarCodigo();
}
async function reenviarCodigo() {
  if(!envioLeitor)return;
  try {
    await requisitar(`/api/leitores/${canalLeitor}/leituras`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(envioLeitor)});
    $('#codigo-manual').value='';aguardarResultado();
  }catch(e){
    statusLeitor(e.message);
    if(e.status && e.status<500){aguardandoLeitor=false;envioLeitor=null;if([403,410].includes(e.status))conectadoLeitor=false;botoesLeitor();}
    else {statusLeitor('Conexão interrompida. Tentando reenviar o mesmo código…');setTimeout(reenviarCodigo,2000);}
  }
}
$('#iniciar-camera').addEventListener('click',async()=>{
  if(!window.isSecureContext || !navigator.mediaDevices?.getUserMedia){statusLeitor('Abra este leitor pelo endereço HTTPS do sistema para permitir a câmera. Você ainda pode enviar códigos manualmente.');return;}
  pararCamera();const geracao=geracaoCamera;$('#iniciar-camera').disabled=true;
  try {
    statusLeitor('Aponte para o código. Se solicitado, permita usar a câmera.');
    const reader=new ZXingBrowser.BrowserMultiFormatOneDReader();
    const controls=await reader.decodeFromConstraints({audio:false,video:{facingMode:{ideal:'environment'}}},$('#camera-leitor'),(result,error,controle)=>{
      if(result && geracao===geracaoCamera && !aguardandoLeitor){controle.stop();enviarCodigo(result.getText());}
    });
    if(geracao!==geracaoCamera)controls.stop();else controlesCamera=controls;
  }catch(e){statusLeitor(e.name==='NotAllowedError'?'Permita o acesso à câmera nas configurações do navegador.':'Não foi possível abrir a câmera. Tente novamente ou digite o código.');botoesLeitor();}
});
$('#parar-camera').addEventListener('click',()=>{pararCamera();botoesLeitor();});
$('#form-leitura-manual').addEventListener('submit',e=>{e.preventDefault();enviarCodigo($('#codigo-manual').value.trim());});
document.addEventListener('visibilitychange',()=>{if(document.hidden){pararCamera();botoesLeitor();}});
window.addEventListener('pagehide',pararCamera);
(async()=>{
  try {
    if(!canalLeitor || !/^[0-9a-f-]{36}$/i.test(canalLeitor))throw new Error('Abra o leitor pelo QR Code gerado no computador.');
    await requisitar(`/api/leitores/${canalLeitor}/conectar`,{method:'POST'});conectadoLeitor=true;statusLeitor('Celular conectado. Toque em Ler próximo produto.');
  }catch(e){statusLeitor(e.message);}botoesLeitor();
})();
