package com.studymatching.common.exception;

import jakarta.persistence.OptimisticLockException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private ResponseEntity<ErrorResponse> buildResponse(ErrorCode code) {
        return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        return buildResponse(e.getErrorCode());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation() {
        return buildResponse(ErrorCode.DATA_INTEGRITY_VIOLATION);
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ErrorResponse> handleOptimisticLock() {
        return buildResponse(ErrorCode.CONCURRENT_MODIFICATION);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst().map(violation -> violation.getMessage())
                .orElse(ErrorCode.INVALID_REQUEST.getMessage());
        return ResponseEntity.badRequest().body(new ErrorResponse(
                ErrorCode.INVALID_REQUEST.name(), 400, message, LocalDateTime.now()));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        ValidationErrorResponse response = new ValidationErrorResponse(
                ErrorCode.VALIDATION_FAILED.name(), status.value(),
                ErrorCode.VALIDATION_FAILED.getMessage(), errors, LocalDateTime.now());
        return new ResponseEntity<>(response, headers, status);
    }

    // Preserve Spring MVC's status and protocol headers (e.g. Allow for 405).
    // Includes malformed JSON, conversion failures and MVC method validation.
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception e, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ErrorCode code = status.is5xxServerError()
                ? ErrorCode.INTERNAL_SERVER_ERROR : ErrorCode.INVALID_REQUEST;
        if (status.is5xxServerError()) {
            log.error("Unhandled MVC server error", e);
        }
        return super.handleExceptionInternal(e, new ErrorResponse(
                code.name(), status.value(), code.getMessage(), LocalDateTime.now()),
                headers, status, request);
    }

    @ExceptionHandler(AuthenticationServiceException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationService(AuthenticationServiceException e) {
        log.error("Authentication service failed", e);
        return buildResponse(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException e) {
        return buildResponse(ErrorCode.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied() {
        return buildResponse(ErrorCode.ACCESS_DENIED);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("Unhandled request failure", e);
        return buildResponse(ErrorCode.INTERNAL_SERVER_ERROR);
    }
}
