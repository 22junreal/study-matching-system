package com.studymatching.auth.exception;

import com.studymatching.common.exception.BusinessException;
import com.studymatching.common.exception.ErrorCode;

public class InvalidRefreshTokenException extends BusinessException {
    public InvalidRefreshTokenException() { super(ErrorCode.INVALID_REFRESH_TOKEN); }
}
