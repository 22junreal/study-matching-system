package com.studymatching.studymember.exception;

import com.studymatching.common.exception.BusinessException;
import com.studymatching.common.exception.ErrorCode;

public class StudyMemberException extends BusinessException {
    public StudyMemberException(ErrorCode code) { super(code); }
}
