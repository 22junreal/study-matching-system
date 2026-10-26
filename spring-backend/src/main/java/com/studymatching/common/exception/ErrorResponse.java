package com.studymatching.common.exception;

import java.time.LocalDateTime;

public record ErrorResponse(
        String code,
        int status,
        String message,
        LocalDateTime timestamp
) {
    public static ErrorResponse of(ErrorCode code) {
        return new ErrorResponse(code.name(), code.getStatus().value(),
                code.getMessage(), LocalDateTime.now());
    }
}
