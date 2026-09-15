package com.studymatching.common.exception;

import com.studymatching.member.exception.MemberNotFoundException;
import com.studymatching.study.exception.StudyAccessDeniedException;
import com.studymatching.study.exception.StudyNotFoundException;
import com.studymatching.studyapplication.exception.DuplicateStudyApplicationException;
import com.studymatching.studyapplication.exception.OwnStudyApplicationException;
import com.studymatching.studyapplication.exception.StudyApplicationAlreadyProcessedException;
import com.studymatching.studyapplication.exception.StudyApplicationCannotCancelException;
import com.studymatching.studyapplication.exception.StudyApplicationCannotReapplyException;
import com.studymatching.studyapplication.exception.StudyApplicationNotFoundException;
import com.studymatching.studyapplication.exception.StudyCapacityExceededException;
import com.studymatching.studyapplication.exception.StudyNotRecruitingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.studymatching.profile.exception.ProfileNotFoundException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private ResponseEntity<ErrorResponse> buildResponse(
            HttpStatus status,
            String message
    ) {
        ErrorResponse response = new ErrorResponse(
                status.value(),
                message,
                LocalDateTime.now()
        );

        return ResponseEntity
                .status(status)
                .body(response);
    }
    @ExceptionHandler(ProfileNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProfileNotFound(
            ProfileNotFoundException e
    ) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                e.getMessage()
        );
    }

    @ExceptionHandler(DuplicateMemberException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateMember(
            DuplicateMemberException e
    ) {
        return buildResponse(
                HttpStatus.CONFLICT,
                e.getMessage()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidation(
            MethodArgumentNotValidException e
    ) {
        Map<String, String> errors = new LinkedHashMap<>();

        e.getBindingResult()
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
            AuthenticationException e
    ) {
        return buildResponse(
                HttpStatus.UNAUTHORIZED,
                "사용자명 또는 비밀번호가 올바르지 않습니다."
        );
    }

    @ExceptionHandler(StudyNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleStudyNotFound(
            StudyNotFoundException e
    ) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                e.getMessage()
        );
    }

    @ExceptionHandler(StudyAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleStudyAccessDenied(
            StudyAccessDeniedException e
    ) {
        return buildResponse(
                HttpStatus.FORBIDDEN,
                e.getMessage()
        );
    }

    @ExceptionHandler(OwnStudyApplicationException.class)
    public ResponseEntity<ErrorResponse> handleOwnStudyApplication(
            OwnStudyApplicationException e
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                e.getMessage()
        );
    }

    @ExceptionHandler(DuplicateStudyApplicationException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateStudyApplication(
            DuplicateStudyApplicationException e
    ) {
        return buildResponse(
                HttpStatus.CONFLICT,
                e.getMessage()
        );
    }

    @ExceptionHandler(StudyNotRecruitingException.class)
    public ResponseEntity<ErrorResponse> handleStudyNotRecruiting(
            StudyNotRecruitingException e
    ) {
        return buildResponse(
                HttpStatus.CONFLICT,
                e.getMessage()
        );
    }

    @ExceptionHandler(StudyApplicationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleStudyApplicationNotFound(
            StudyApplicationNotFoundException e
    ) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                e.getMessage()
        );
    }

    @ExceptionHandler(StudyApplicationAlreadyProcessedException.class)
    public ResponseEntity<ErrorResponse> handleStudyApplicationAlreadyProcessed(
            StudyApplicationAlreadyProcessedException e
    ) {
        return buildResponse(
                HttpStatus.CONFLICT,
                e.getMessage()
        );
    }

    @ExceptionHandler(MemberNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleMemberNotFound(
            MemberNotFoundException e
    ) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                e.getMessage()
        );
    }

    @ExceptionHandler(StudyCapacityExceededException.class)
    public ResponseEntity<ErrorResponse> handleStudyCapacityExceeded(
            StudyCapacityExceededException e
    ) {
        return buildResponse(
                HttpStatus.CONFLICT,
                e.getMessage()
        );
    }

    @ExceptionHandler(StudyApplicationCannotCancelException.class)
    public ResponseEntity<ErrorResponse> handleStudyApplicationCannotCancel(
            StudyApplicationCannotCancelException e
    ) {
        return buildResponse(
                HttpStatus.CONFLICT,
                e.getMessage()
        );
    }

    @ExceptionHandler(StudyApplicationCannotReapplyException.class)
    public ResponseEntity<ErrorResponse> handleStudyApplicationCannotReapply(
            StudyApplicationCannotReapplyException e
    ) {
        return buildResponse(
                HttpStatus.CONFLICT,
                e.getMessage()
        );
    }
}