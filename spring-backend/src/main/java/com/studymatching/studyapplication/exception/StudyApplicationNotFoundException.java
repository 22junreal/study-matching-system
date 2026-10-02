package com.studymatching.studyapplication.exception;

import com.studymatching.common.exception.BusinessException;
import com.studymatching.common.exception.ErrorCode;

public class StudyApplicationNotFoundException extends BusinessException {

    public StudyApplicationNotFoundException() {
        super(ErrorCode.STUDY_APPLICATION_NOT_FOUND);
    }
}
