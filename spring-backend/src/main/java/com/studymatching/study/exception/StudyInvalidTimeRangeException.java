package com.studymatching.study.exception;

import com.studymatching.common.exception.BusinessException;
import com.studymatching.common.exception.ErrorCode;

public class StudyInvalidTimeRangeException extends BusinessException {

    public StudyInvalidTimeRangeException() {
        super(ErrorCode.STUDY_INVALID_TIME_RANGE);
    }
}
