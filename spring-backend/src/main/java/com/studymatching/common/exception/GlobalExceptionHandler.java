package com.studymatching.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.security.core.AuthenticationException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateMemberException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateMember(
            DuplicateMemberException exception
    ) {
        ErrorResponse response = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                exception.getMessage(),
                LocalDateTime.now()
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidation(
            MethodArgumentNotValidException exception
    ) {

        Map<String, String> errors = new LinkedHashMap<>();

        exception.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        errors.put(
                                error.getField(),
                                error.getDefaultMessage()
                        )
                );

        ValidationErrorResponse response =
                new ValidationErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        "입력값이 올바르지 않습니다.",
                        errors,
                        LocalDateTime.now()
                );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(
            AuthenticationException exception
    ) {

        ErrorResponse response = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                "사용자명 또는 비밀번호가 올바르지 않습니다.",
                LocalDateTime.now()
        );

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(response);
    }
}