package com.seatlock.payment.shared.web;

import com.seatlock.payment.shared.exception.BadRequestException;
import com.seatlock.payment.shared.exception.ConflictException;
import com.seatlock.payment.shared.exception.DownstreamException;
import com.seatlock.payment.shared.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessResourceFailureException;
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

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        return rejected(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail handleBadRequest(BadRequestException ex) {
        return rejected(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        return rejected(HttpStatus.CONFLICT, ex.getMessage());
    }

    // Details (Razorpay's error body, backend status) stay in the logs
    @ExceptionHandler(DownstreamException.class)
    public ProblemDetail handleDownstream(DownstreamException ex) {
        log.error(ex.getMessage(), ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    @ExceptionHandler(DataAccessResourceFailureException.class)
    public ProblemDetail handleUnavailable(DataAccessResourceFailureException ex) {
        log.error("Data store unavailable", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Service temporarily unavailable");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        log.info("Rejected with {}: validation failed {}", status.value(), errors);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "Validation failed");
        problem.setProperty("errors", errors);
        return ResponseEntity.status(status).body(problem);
    }

    // Spring's own errors (unreadable JSON, wrong method, unknown path, ...)
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        if (statusCode.is4xxClientError()) {
            log.info("Rejected with {}: {}", statusCode.value(), ex.getMessage());
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
    }

    private static ProblemDetail rejected(HttpStatus status, String detail) {
        log.info("Rejected with {}: {}", status.value(), detail);
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}
