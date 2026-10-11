package br.com.distribuidora.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.ProblemDetail;

@RestControllerAdvice(assignableTypes = AssistenteController.class)
public class AssistenteExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail tratar(ResponseStatusException e) {
        return ProblemDetail.forStatusAndDetail(e.getStatusCode(), e.getReason() == null ? "Não foi possível responder." : e.getReason());
    }
}
