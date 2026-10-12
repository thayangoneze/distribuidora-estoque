package br.com.distribuidora;
import br.com.distribuidora.service.LeitorService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
class LeitorTest {
    @Test void telefoneEnviaPcConfirmaEReenvioNaoDuplica() {
        var service=new LeitorService();var canal=service.criar("pc");
        assertTrue(canal.expiraEm().isAfter(Instant.now()));assertFalse(canal.conectado());
        service.conectar(canal.id(),"phone");UUID evento=UUID.randomUUID();
        service.enviar(canal.id(),"phone",evento,"0123456789012");service.enviar(canal.id(),"phone",evento,"0123456789012");
        assertEquals(1,service.pendentes(canal.id(),"pc").size());
        service.confirmar(canal.id(),"pc",evento,true,"Adicionado");
        assertTrue(service.pendentes(canal.id(),"pc").isEmpty());
        assertEquals("ACEITO",service.resultado(canal.id(),"phone",evento).status());
        assertEquals("ACEITO",service.enviar(canal.id(),"phone",evento,"0123456789012").status());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.enviar(canal.id(),"phone",evento,"7891234567890")).getStatusCode().value());
    }
    @Test void outroCaixaOuTelefoneNaoAcessaConexao() {
        var s=new LeitorService();var c=s.criar("pc");s.conectar(c.id(),"phone");
        assertEquals(403,assertThrows(ResponseStatusException.class,()->s.pendentes(c.id(),"otherPC")).getStatusCode().value());
        assertEquals(403,assertThrows(ResponseStatusException.class,()->s.enviar(c.id(),"otherPhone",UUID.randomUUID(),"0123456789012")).getStatusCode().value());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->s.conectar(c.id(),"otherPhone")).getStatusCode().value());
    }
    @Test void novaConexaoInvalidaAnteriorEEncerrarDesligaTelefone() {
        var s=new LeitorService();var c=s.criar("pc");s.criar("pc");
        assertEquals(410,assertThrows(ResponseStatusException.class,()->s.conectar(c.id(),"phone")).getStatusCode().value());
        var novo=s.criar("pc");s.conectar(novo.id(),"phone");s.encerrar(novo.id(),"pc");
        assertEquals(410,assertThrows(ResponseStatusException.class,()->s.enviar(novo.id(),"phone",UUID.randomUUID(),"0123456789012")).getStatusCode().value());
    }
    @Test void leituraPendenteBloqueiaOutraAteResposta() {
        var s=new LeitorService();var c=s.criar("pc");s.conectar(c.id(),"phone");UUID id=UUID.randomUUID();
        s.enviar(c.id(),"phone",id,"0123456789012");
        assertEquals(409,assertThrows(ResponseStatusException.class,()->s.enviar(c.id(),"phone",UUID.randomUUID(),"0123456789012")).getStatusCode().value());
        s.confirmar(c.id(),"pc",id,false,"Sem estoque");
        assertEquals("REJEITADO",s.resultado(c.id(),"phone",id).status());
        assertEquals("PENDENTE",s.enviar(c.id(),"phone",UUID.randomUUID(),"0123456789012").status());
    }
}
