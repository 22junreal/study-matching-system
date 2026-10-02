package com.studymatching.studyapplication.exception;

import com.studymatching.common.exception.BusinessException;
import com.studymatching.common.exception.ErrorCode;

public class StudyApplicationCannotCancelException extends BusinessException {

    public StudyApplicationCannotCancelException() {
        super(ErrorCode.STUDY_APPLICATION_CANNOT_CANCEL);
    }
}
