package com.ojttrack.backend.config;

import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns exceptions thrown by any controller into consistent JSON error responses
 * ("Problem Details", RFC 9457): {"status": 400, "detail": "...", ...}.
 *
 * Extending ResponseEntityExceptionHandler already covers Spring's own exceptions,
 * including ResponseStatusException (e.g. our 404 "Application not found" and
 * 409 "email already exists"), so their messages reach the frontend.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /** @Valid failures: list which fields were wrong instead of a generic message. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, detail.isEmpty() ? "Invalid request" : detail);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    /**
     * A database constraint rejected the write (e.g. two requests creating the same
     * email at the same moment). That is a conflict, not a server crash.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "The request conflicts with existing data.");
    }
}
