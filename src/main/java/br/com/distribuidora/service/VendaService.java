package br.com.distribuidora.service;
import br.com.distribuidora.dto.NovaVenda;
import br.com.distribuidora.model.*;
import br.com.distribuidora.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.security.MessageDigest;
import java.util.*;
@Service
public class VendaService {
    private final ProdutoRepository produtos;
    private final MovimentacaoRepository movimentos;
    private final VendaRepository vendas;
    private final ObjectMapper mapper;
    public VendaService(ProdutoRepository produtos,MovimentacaoRepository movimentos,VendaRepository vendas,ObjectMapper mapper) {
        this.produtos=produtos;this.movimentos=movimentos;this.vendas=vendas;this.mapper=mapper;
    }
    @Transactional
    public Venda finalizar(NovaVenda dados) {
        String assinatura;
        try { assinatura=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(dados))); }
        catch(Exception e) { throw erro(HttpStatus.INTERNAL_SERVER_ERROR,"Não foi possível validar a venda."); }
        var existente=vendas.findById(dados.id());
        if(existente.isPresent())return repetida(existente.get(),assinatura);
        Map<Long,Produto> bloqueados=new TreeMap<>();
        if(dados.itens().stream().map(NovaVenda.Item::produtoId).distinct().count()!=dados.itens().size())
            throw erro(HttpStatus.BAD_REQUEST,"Agrupe as quantidades de cada produto em um único item.");
        dados.itens().stream().map(NovaVenda.Item::produtoId).sorted().forEach(id -> bloqueados.put(id,produtos.buscarParaAtualizar(id)
            .orElseThrow(() -> erro(HttpStatus.CONFLICT,"Um produto não está mais cadastrado. Revise o carrinho."))));
        existente=vendas.findById(dados.id());
        if(existente.isPresent())return repetida(existente.get(),assinatura);
        Venda venda=new Venda();venda.id=dados.id();venda.assinatura=assinatura;venda.criadaEm=Instant.now();
        venda.pagamento=dados.pagamento();venda.subtotal=BigDecimal.ZERO;
        for(var item:dados.itens()) {
            Produto p=bloqueados.get(item.produtoId());
            if(p.saldo<item.quantidade())throw erro(HttpStatus.CONFLICT,"Estoque insuficiente para "+p.nome+". Revise o carrinho.");
            if(p.precoVenda==null)throw erro(HttpStatus.CONFLICT,"Informe o preço de venda de "+p.nome+" na página Produtos.");
            if(p.precoVenda.compareTo(item.precoUnitario())!=0)throw erro(HttpStatus.CONFLICT,"O preço de "+p.nome+" mudou. Atualize os produtos e revise a venda.");
            Venda.Item registro=new Venda.Item();registro.produtoId=p.id;registro.nome=p.nome;registro.codigoBarras=p.codigoBarras;
            registro.unidade=p.unidadeEstoque==null?"UN":p.unidadeEstoque;registro.quantidade=item.quantidade();registro.precoUnitario=p.precoVenda;
            registro.subtotal=p.precoVenda.multiply(BigDecimal.valueOf(item.quantidade())).setScale(2);
            venda.itens.add(registro);venda.subtotal=venda.subtotal.add(registro.subtotal);
        }
        if(venda.subtotal.compareTo(new BigDecimal("999999999999.99"))>0)throw erro(HttpStatus.BAD_REQUEST,"O total excede o limite desta versão do caixa.");
        venda.desconto=dados.desconto();
        if(venda.desconto.compareTo(venda.subtotal)>0)throw erro(HttpStatus.BAD_REQUEST,"O desconto não pode superar o subtotal.");
        venda.total=venda.subtotal.subtract(venda.desconto).setScale(2);venda.troco=BigDecimal.ZERO.setScale(2);
        if(venda.pagamento==Venda.Pagamento.DINHEIRO) {
            if(dados.valorRecebido()==null || dados.valorRecebido().compareTo(venda.total)<0)
                throw erro(HttpStatus.BAD_REQUEST,"O valor recebido em dinheiro deve cobrir o total.");
            venda.valorRecebido=dados.valorRecebido();venda.troco=venda.valorRecebido.subtract(venda.total).setScale(2);
        }
        // Estoque e registro financeiro são gravados na mesma transação.
        for(var item:dados.itens()) {
            Produto p=bloqueados.get(item.produtoId());p.saldo-=item.quantidade();
            Movimentacao movimento=new Movimentacao(p,Movimentacao.Tipo.SAIDA,item.quantidade());movimento.vendaId=venda.id;
            movimentos.save(movimento);
        }
        try { return vendas.saveAndFlush(venda); }
        catch(DataIntegrityViolationException e) { throw erro(HttpStatus.CONFLICT,"Venda já enviada. Consulte as últimas vendas antes de tentar novamente."); }
    }
    private Venda repetida(Venda venda,String assinatura) {
        if(!venda.assinatura.equals(assinatura))throw erro(HttpStatus.CONFLICT,"Este identificador já pertence a outra venda.");
        return venda;
    }
    @Transactional(readOnly=true) public List<Venda> recentes() { return vendas.findTop20ByOrderByCriadaEmDesc(); }
    private static ResponseStatusException erro(HttpStatus s,String texto) { return new ResponseStatusException(s,texto); }
}
