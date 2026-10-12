package br.com.distribuidora.service;

import br.com.distribuidora.dto.*;
import br.com.distribuidora.model.*;
import br.com.distribuidora.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.security.MessageDigest;
import java.util.*;

@Service
public class ImportacaoNotaService {
    private final ImportacaoNotaRepository notas;
    private final ProdutoRepository produtos;
    private final MovimentacaoRepository movimentacoes;
    private final GeminiEstoqueClient ia;
    private final ObjectMapper mapper;
    private final Validator validator;
    public ImportacaoNotaService(ImportacaoNotaRepository notas, ProdutoRepository produtos,
        MovimentacaoRepository movimentacoes, GeminiEstoqueClient ia, ObjectMapper mapper, Validator validator) {
        this.notas=notas; this.produtos=produtos; this.movimentacoes=movimentacoes;
        this.ia=ia; this.mapper=mapper; this.validator=validator;
    }
    public record Revisao(UUID importacaoId, NotaExtraida nota) {}
    public record Resultado(UUID importacaoId, int itensRegistrados, String confirmadaEm) {}

    public Revisao analisar(MultipartFile arquivo) {
        if (arquivo.isEmpty() || arquivo.getSize()>8*1024*1024)
            throw falha(HttpStatus.BAD_REQUEST,"Envie um arquivo de até 8 MB, que não esteja vazio.");
        byte[] bytes;
        String hash;
        try {
            bytes=arquivo.getBytes();
            hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) { throw falha(HttpStatus.BAD_REQUEST,"Não foi possível ler o arquivo."); }
        String mime=detectarMime(bytes);
        var existente=notas.findByHashArquivo(hash);
        if (existente.isPresent()) return revisar(existente.get());
        NotaExtraida extraida=ia.extrairNota(bytes,mime);
        if (extraida==null || !validator.validate(extraida).isEmpty())
            throw falha(HttpStatus.UNPROCESSABLE_ENTITY,"Não foi possível identificar itens válidos. Confira a legibilidade da nota.");
        ImportacaoNota nota=new ImportacaoNota(); nota.hashArquivo=hash;
        nota.dadosExtraidos=serializar(extraida);
        try { return revisar(notas.saveAndFlush(nota)); }
        catch (DataIntegrityViolationException e) {
            return revisar(notas.findByHashArquivo(hash).orElseThrow(() ->
                falha(HttpStatus.CONFLICT,"A nota está sendo analisada. Tente novamente.")));
        }
    }
    private Revisao revisar(ImportacaoNota nota) {
        if(nota.confirmadaEm!=null) throw falha(HttpStatus.CONFLICT,"Este arquivo de nota já foi importado. Nenhuma entrada foi repetida.");
        try { return new Revisao(nota.id,mapper.readValue(nota.dadosExtraidos,NotaExtraida.class)); }
        catch(Exception e) { throw falha(HttpStatus.INTERNAL_SERVER_ERROR,"Não foi possível recuperar a revisão da nota."); }
    }
    @Transactional
    public Resultado confirmar(UUID id, ConfirmacaoNota dados) {
        if(!validator.validate(dados).isEmpty()) throw falha(HttpStatus.BAD_REQUEST,"Revise os itens, as quantidades e marque a confirmação.");
        ImportacaoNota nota=notas.buscarParaConfirmar(id).orElseThrow(() -> falha(HttpStatus.NOT_FOUND,"Revisão de nota não encontrada. Envie a nota novamente."));
        if(nota.confirmadaEm!=null) throw falha(HttpStatus.CONFLICT,"Esta nota já foi confirmada. Nenhuma entrada foi repetida.");
        if(dados.dataEmissao()!=null && !dados.dataEmissao().isBlank()) {
            try { LocalDate.parse(dados.dataEmissao()); }
            catch(Exception e) { throw falha(HttpStatus.BAD_REQUEST,"Confira a data de emissão da nota."); }
        }
        // Ordem estável de locks evita que duas importações bloqueiem os mesmos produtos em ordem inversa.
        Map<Long,Produto> existentes=new HashMap<>();
        dados.itens().stream().map(ConfirmacaoNota.Item::produtoId).filter(Objects::nonNull).distinct().sorted().forEach(produtoId -> {
            existentes.put(produtoId,produtos.buscarParaAtualizar(produtoId).orElseThrow(() ->
                falha(HttpStatus.CONFLICT,"Um produto selecionado foi apagado. Recarregue a lista e revise os itens.")));
        });
        Map<String,Produto> novos=new HashMap<>();
        for(var item:dados.itens()) {
            Produto produto;
            if(item.produtoId()!=null) produto=existentes.get(item.produtoId());
            else {
                String nome=item.nome().trim();
                if(nome.isBlank()) throw falha(HttpStatus.BAD_REQUEST,"Informe o nome de cada produto novo.");
                String nomeNormalizado=nome.toLowerCase(Locale.ROOT);
                produto=novos.get(nomeNormalizado);
                if(produto==null) {
                    if(!produtos.findByNomeIgnoreCase(nome).isEmpty())
                        throw falha(HttpStatus.CONFLICT,"Já existe um produto com o nome '"+nome+"'. Selecione-o na revisão para evitar duplicação.");
                    produto=new Produto(); produto.nome=nome; produto.unidadeEstoque=item.unidadeEstoque();
                    produto.estoqueSeguranca=item.estoqueSeguranca(); produto.prazoEntregaDias=item.prazoEntregaDias();
                    produto.coberturaDesejadaDias=item.coberturaDesejadaDias();
                    produto=produtos.save(produto); novos.put(nomeNormalizado,produto);
                }
            }
            String unidade=produto.unidadeEstoque==null ? "UN" : produto.unidadeEstoque;
            if(!unidade.equals(item.unidadeEstoque())) throw falha(HttpStatus.BAD_REQUEST,
                "A quantidade deve estar na unidade de estoque do produto: "+produto.nome+" ("+unidade+").");
            if((long)produto.saldo+item.quantidadeEstoque()>Integer.MAX_VALUE)
                throw falha(HttpStatus.BAD_REQUEST,"A quantidade excede o limite de saldo permitido.");
            produto.saldo+=item.quantidadeEstoque();
            Movimentacao movimento=new Movimentacao(produto,Movimentacao.Tipo.ENTRADA,item.quantidadeEstoque());
            movimento.importacaoNota=nota; movimento.valorUnitarioNota=item.valorUnitarioNota();
            movimento.quantidadeNota=item.quantidadeNota(); movimento.unidadeNota=item.unidadeNota();
            movimentacoes.save(movimento);
        }
        nota.fornecedor=dados.fornecedor(); nota.numeroNota=dados.numeroNota(); nota.dataEmissao=dados.dataEmissao();
        nota.dadosConfirmados=serializar(dados); nota.confirmadaEm=Instant.now();
        notas.save(nota);
        return new Resultado(nota.id,dados.itens().size(),nota.confirmadaEm.toString());
    }
    private String serializar(Object dados) {
        try { return mapper.writeValueAsString(dados); }
        catch(Exception e) { throw falha(HttpStatus.INTERNAL_SERVER_ERROR,"Não foi possível salvar a revisão."); }
    }
    private static String detectarMime(byte[] b) {
        if(b.length>=8 && b[0]==(byte)0x89 && b[1]=='P' && b[2]=='N' && b[3]=='G' && b[4]==13 && b[5]==10 && b[6]==26 && b[7]==10) return "image/png";
        if(b.length>=3 && b[0]==(byte)0xff && b[1]==(byte)0xd8 && b[2]==(byte)0xff) return "image/jpeg";
        if(b.length>=12 && b[0]=='R' && b[1]=='I' && b[2]=='F' && b[3]=='F' && b[8]=='W' && b[9]=='E' && b[10]=='B' && b[11]=='P') return "image/webp";
        if(b.length>=5 && b[0]=='%' && b[1]=='P' && b[2]=='D' && b[3]=='F' && b[4]=='-') return "application/pdf";
        throw falha(HttpStatus.BAD_REQUEST,"Formato não suportado. Envie JPG, PNG, WEBP ou PDF.");
    }
    private static ResponseStatusException falha(HttpStatus status,String mensagem) { return new ResponseStatusException(status,mensagem); }
}
