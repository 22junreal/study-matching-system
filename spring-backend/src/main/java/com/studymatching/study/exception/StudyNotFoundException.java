package com.studymatching.study.exception;

import com.studymatching.common.exception.BusinessException;
import com.studymatching.common.exception.ErrorCode;

public class StudyNotFoundException extends BusinessException {

    public StudyNotFoundException() {
        super(ErrorCode.STUDY_NOT_FOUND);
    }
}
