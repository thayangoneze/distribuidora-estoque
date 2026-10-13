package br.com.distribuidora.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.ProblemDetail;

@RestControllerAdvice(assignableTypes = {AssistenteController.class, EstoqueController.class, FornecedorController.class, VendaController.class})
public class AssistenteExceptionHandler {
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ProblemDetail conflito() {return ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatus.CONFLICT,"Dados já cadastrados ou operação em conflito. Atualize e confira os registros.");}
    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail tratar(ResponseStatusException e) {
        return ProblemDetail.forStatusAndDetail(e.getStatusCode(), e.getReason() == null ? "Não foi possível responder." : e.getReason());
    }
}
