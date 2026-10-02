package com.studymatching.common.exception;

public class DuplicateMemberException extends BusinessException {

    public DuplicateMemberException(ErrorCode errorCode) {
        super(errorCode);
    }
}
