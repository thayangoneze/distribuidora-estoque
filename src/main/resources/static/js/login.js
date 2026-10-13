const mensagemLogin = document.querySelector('#mensagem-login');
const parametros = new URLSearchParams(location.search);
if (parametros.has('error')) {
  mensagemLogin.textContent = 'Usuário ou senha incorretos. Tente novamente.';
  mensagemLogin.hidden = false;
} else if (parametros.has('logout')) {
  mensagemLogin.textContent = 'Você saiu do sistema.';
  mensagemLogin.hidden = false;
}
fetch('/csrf', { cache: 'no-store' }).then(async resposta => {
  if (!resposta.ok) throw new Error('Não foi possível preparar o login. Atualize a página.');
  const csrf = await resposta.json();
  const campo = document.querySelector('#token-login');
  campo.name = csrf.parameterName;
  campo.value = csrf.token;
  document.querySelector('#entrar').disabled = false;
}).catch(erro => {
  mensagemLogin.textContent = erro.message;
  mensagemLogin.hidden = false;
});
