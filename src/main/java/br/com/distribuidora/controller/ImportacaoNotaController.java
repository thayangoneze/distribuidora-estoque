package br.com.distribuidora.controller;

import br.com.distribuidora.service.ImportacaoNotaService;
import br.com.distribuidora.dto.ConfirmacaoNota;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

@RestController
@RequestMapping("/api/notas")
public class ImportacaoNotaController {
    private final ImportacaoNotaService notas;
    public ImportacaoNotaController(ImportacaoNotaService notas) { this.notas=notas; }
    @PostMapping(value="/analisar",consumes="multipart/form-data")
    public ImportacaoNotaService.Revisao analisar(@RequestParam("arquivo") MultipartFile arquivo) { return notas.analisar(arquivo); }
    @PostMapping("/{id}/confirmar")
    public ImportacaoNotaService.Resultado confirmar(@PathVariable UUID id,@Valid @RequestBody ConfirmacaoNota dados) { return notas.confirmar(id,dados); }
}
