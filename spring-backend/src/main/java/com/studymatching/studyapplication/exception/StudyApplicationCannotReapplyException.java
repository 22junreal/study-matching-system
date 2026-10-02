package com.studymatching.studyapplication.exception;

import com.studymatching.common.exception.BusinessException;
import com.studymatching.common.exception.ErrorCode;

public class StudyApplicationCannotReapplyException extends BusinessException {

    public StudyApplicationCannotReapplyException() {
        super(ErrorCode.STUDY_APPLICATION_CANNOT_REAPPLY);
    }
}
