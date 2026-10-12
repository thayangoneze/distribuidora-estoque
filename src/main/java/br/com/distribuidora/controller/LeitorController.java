package br.com.distribuidora.controller;
import br.com.distribuidora.service.LeitorService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.net.URI;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import com.google.zxing.*;
import com.google.zxing.qrcode.QRCodeWriter;
@RestController @RequestMapping("/api/leitores")
public class LeitorController {
    private final LeitorService service;
    public LeitorController(LeitorService service){this.service=service;}
    public record NovaLeitura(@NotNull UUID id,@NotNull @Pattern(regexp="[0-9]{8,14}") String codigo) {}
    public record Confirmacao(boolean aceito,@NotBlank @Size(max=200) String mensagem) {}
    @PostMapping public LeitorService.Conexao criar(HttpSession s){return service.criar(s.getId());}
    @GetMapping("/{id}") public LeitorService.Conexao status(@PathVariable UUID id,HttpSession s){return service.status(id,s.getId());}
    @PostMapping("/{id}/conectar") public LeitorService.Conexao conectar(@PathVariable UUID id,HttpSession s){return service.conectar(id,s.getId());}
    @GetMapping("/{id}/leituras") public List<LeitorService.Leitura> pendentes(@PathVariable UUID id,HttpSession s){return service.pendentes(id,s.getId());}
    @PostMapping("/{id}/leituras") public LeitorService.Leitura ler(@PathVariable UUID id,@Valid @RequestBody NovaLeitura l,HttpSession s){return service.enviar(id,s.getId(),l.id(),l.codigo());}
    @GetMapping("/{id}/leituras/{leitura}") public LeitorService.Leitura resultado(@PathVariable UUID id,@PathVariable UUID leitura,HttpSession s){return service.resultado(id,s.getId(),leitura);}
    @PostMapping("/{id}/leituras/{leitura}/confirmar") public LeitorService.Leitura confirmar(@PathVariable UUID id,@PathVariable UUID leitura,@Valid @RequestBody Confirmacao dados,HttpSession s){return service.confirmar(id,s.getId(),leitura,dados.aceito(),dados.mensagem());}
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void encerrar(@PathVariable UUID id,HttpSession s){service.encerrar(id,s.getId());}
    @GetMapping(value="/{id}/qr",produces=MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qr(@PathVariable UUID id,@RequestParam String origem,HttpSession s) throws Exception {
        service.status(id,s.getId());
        URI uri;
        try{uri=URI.create(origem);}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Informe o endereço HTTPS do sistema.");}
        if(!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null || (uri.getPath()!=null && !uri.getPath().isEmpty() && !uri.getPath().equals("/")))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Informe somente o endereço base HTTPS do sistema.");
        String link=origem.replaceAll("/+$","")+"/leitor.html?conexao="+id;
        var matriz=new QRCodeWriter().encode(link,BarcodeFormat.QR_CODE,320,320);
        BufferedImage imagem=new BufferedImage(320,320,BufferedImage.TYPE_INT_RGB);
        for(int x=0;x<320;x++)for(int y=0;y<320;y++)imagem.setRGB(x,y,matriz.get(x,y)?0x000000:0xffffff);
        ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(imagem,"PNG",out);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(out.toByteArray());
    }
}
