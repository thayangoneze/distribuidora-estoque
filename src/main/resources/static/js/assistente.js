let historicoIa = [];
let iaDisponivel = false;
let iaOcupada = false;
const conversaIa = $('#conversa-ia');
function mensagemIa(papel, texto) {
  const bloco = document.createElement('div');
  bloco.className = `mensagem-chat ${papel === 'USUARIO' ? 'pergunta-chat' : 'resposta-chat'}`;
  const titulo = document.createElement('strong');
  titulo.textContent = papel === 'USUARIO' ? 'Você' : 'Assistente de estoque';
  const conteudo = document.createElement('p');
  conteudo.textContent = texto;
  bloco.append(titulo, conteudo);
  conversaIa.append(bloco);
  conversaIa.scrollTop = conversaIa.scrollHeight;
  return bloco;
}
async function verificarIa() {
  try {
    const estado = await requisitar('/api/assistente/status');
    iaDisponivel = estado.configurado;
    $('#status-ia').textContent = iaDisponivel ? 'Gemini configurado. Cada pergunta consulta os dados atuais do estoque.' : 'A IA ainda não foi ativada. O administrador precisa configurar a integração.';
    $('#enviar-ia').disabled = !iaDisponivel;
  } catch (erro) { $('#status-ia').textContent = erro.message; }
}
function novaConversaIa() {
  if (iaOcupada) return;
  historicoIa = [];
  conversaIa.replaceChildren();
  $('#erro-ia').hidden = true;
}
$('#nova-conversa').addEventListener('click', novaConversaIa);
$('#periodo-ia').addEventListener('change', novaConversaIa);
document.querySelectorAll('.perguntas-rapidas button').forEach(botao => {
  botao.addEventListener('click', () => {
    $('#pergunta-ia').value = botao.textContent;
    $('#pergunta-ia').focus();
  });
});
$('#form-assistente').addEventListener('submit', async evento => {
  evento.preventDefault();
  if (!iaDisponivel || iaOcupada) return;
  const pergunta = $('#pergunta-ia').value.trim();
  if (!pergunta) return;
  iaOcupada = true;
  $('#enviar-ia').disabled = true;
  $('#enviar-ia').textContent = 'Consultando o estoque…';
  $('#nova-conversa').disabled = true;
  $('#periodo-ia').disabled = true;
  $('#erro-ia').hidden = true;
  conversaIa.querySelector('.boas-vindas')?.remove();
  const perguntaElemento = mensagemIa('USUARIO', pergunta);
  const esperando = mensagemIa('ASSISTENTE', 'Analisando os dados do estoque…');
  try {
    const resposta = await requisitar('/api/assistente/perguntas', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ pergunta, periodoDias: Number($('#periodo-ia').value), historico: historicoIa.slice(-8) })
    });
    esperando.remove();
    mensagemIa('ASSISTENTE', resposta.resposta);
    historicoIa.push({ papel: 'USUARIO', texto: pergunta }, { papel: 'ASSISTENTE', texto: resposta.resposta.slice(0, 6000) });
    historicoIa = historicoIa.slice(-8);
    $('#pergunta-ia').value = '';
    $('#status-ia').textContent = `Consulta de ${resposta.periodoDias} dias. Dados atualizados em ${new Date(resposta.atualizadoEm).toLocaleString('pt-BR')}.`;
  } catch (erro) {
    esperando.remove(); perguntaElemento.remove();
    $('#erro-ia').textContent = erro.message;
    $('#erro-ia').hidden = false;
  } finally {
    iaOcupada = false;
    $('#enviar-ia').disabled = !iaDisponivel;
    $('#enviar-ia').textContent = 'Enviar pergunta';
    $('#nova-conversa').disabled = false;
    $('#periodo-ia').disabled = false;
  }
});
verificarIa();
