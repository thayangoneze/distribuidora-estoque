package br.com.distribuidora.service;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
@Service
public class LeitorService {
    private final Map<UUID,Canal> canais=new ConcurrentHashMap<>();
    public static class Canal {
        public UUID id; public Instant expiraEm; public String computador; public String celular;
        public final Map<UUID,Leitura> leituras=new LinkedHashMap<>();
    }
    public record Conexao(UUID id,Instant expiraEm,boolean conectado) {}
    public record Leitura(UUID id,String codigo,String status,String mensagem) {}
    public synchronized Conexao criar(String computador) {
        canais.entrySet().removeIf(e -> !e.getValue().expiraEm.isAfter(Instant.now()));
        canais.entrySet().removeIf(e -> e.getValue().computador.equals(computador));
        if(canais.size()>=100)throw erro(HttpStatus.TOO_MANY_REQUESTS,"Limite de caixas conectados. Tente mais tarde.");
        Canal c=new Canal();c.id=UUID.randomUUID();c.computador=computador;c.expiraEm=Instant.now().plus(Duration.ofMinutes(30));canais.put(c.id,c);
        return resumo(c);
    }
    private Canal canal(UUID id) {
        Canal c=canais.get(id);
        if(c==null || !c.expiraEm.isAfter(Instant.now())) { canais.remove(id);throw erro(HttpStatus.GONE,"Conexão encerrada ou expirada. Gere outro QR Code no caixa."); }
        return c;
    }
    public Conexao conectar(UUID id,String celular) {
        Canal c=canal(id);synchronized(c){
            if(c.celular!=null && !c.celular.equals(celular))throw erro(HttpStatus.CONFLICT,"Este caixa já está conectado a outro celular.");
            c.celular=celular;return resumo(c);
        }
    }
    private void dono(Canal c,String sessao) {if(!c.computador.equals(sessao))throw erro(HttpStatus.FORBIDDEN,"Conexão de outro caixa.");}
    private void telefone(Canal c,String sessao) {if(!Objects.equals(c.celular,sessao))throw erro(HttpStatus.FORBIDDEN,"Conecte este celular pelo QR Code do caixa.");}
    public Conexao status(UUID id,String sessao) {Canal c=canal(id);synchronized(c){dono(c,sessao);return resumo(c);}}
    public List<Leitura> pendentes(UUID id,String sessao) {Canal c=canal(id);synchronized(c){dono(c,sessao);return c.leituras.values().stream().filter(l->l.status().equals("PENDENTE")).toList();}}
    public Leitura enviar(UUID id,String sessao,UUID leitura,String codigo) {
        Canal c=canal(id);synchronized(c){
            telefone(c,sessao);Leitura antiga=c.leituras.get(leitura);
            if(antiga!=null){if(!antiga.codigo().equals(codigo))throw erro(HttpStatus.CONFLICT,"Identificador já usado por outro código.");return antiga;}
            if(c.leituras.values().stream().anyMatch(l->l.status().equals("PENDENTE")))throw erro(HttpStatus.CONFLICT,"Aguarde o caixa confirmar a leitura anterior.");
            if(c.leituras.size()>=2000)throw erro(HttpStatus.TOO_MANY_REQUESTS,"Limite de leituras. Conecte novamente o celular.");
            Leitura l=new Leitura(leitura,codigo,"PENDENTE","Aguardando o computador.");c.leituras.put(leitura,l);return l;
        }
    }
    public Leitura resultado(UUID id,String sessao,UUID leitura) {Canal c=canal(id);synchronized(c){telefone(c,sessao);return Optional.ofNullable(c.leituras.get(leitura)).orElseThrow(()->erro(HttpStatus.NOT_FOUND,"Leitura não encontrada."));}}
    public Leitura confirmar(UUID id,String sessao,UUID leitura,boolean aceito,String mensagem) {
        Canal c=canal(id);synchronized(c){dono(c,sessao);Leitura l=Optional.ofNullable(c.leituras.get(leitura)).orElseThrow(()->erro(HttpStatus.NOT_FOUND,"Leitura não encontrada."));
            if(!l.status().equals("PENDENTE"))return l;
            Leitura resultado=new Leitura(l.id(),l.codigo(),aceito?"ACEITO":"REJEITADO",mensagem);c.leituras.put(leitura,resultado);return resultado;
        }
    }
    public void encerrar(UUID id,String sessao) {Canal c=canal(id);synchronized(c){dono(c,sessao);canais.remove(id);}}
    private Conexao resumo(Canal c){return new Conexao(c.id,c.expiraEm,c.celular!=null);}
    private static ResponseStatusException erro(HttpStatus status,String texto){return new ResponseStatusException(status,texto);}
}
