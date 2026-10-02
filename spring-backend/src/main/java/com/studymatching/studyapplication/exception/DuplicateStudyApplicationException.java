package com.studymatching.studyapplication.exception;

import com.studymatching.common.exception.BusinessException;
import com.studymatching.common.exception.ErrorCode;

public class DuplicateStudyApplicationException extends BusinessException {

    public DuplicateStudyApplicationException() {
        super(ErrorCode.DUPLICATE_STUDY_APPLICATION);
    }
}
