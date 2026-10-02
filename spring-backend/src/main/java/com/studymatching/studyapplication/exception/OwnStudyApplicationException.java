package com.studymatching.studyapplication.exception;

import com.studymatching.common.exception.BusinessException;
import com.studymatching.common.exception.ErrorCode;

public class OwnStudyApplicationException extends BusinessException {

    public OwnStudyApplicationException() {
        super(ErrorCode.OWN_STUDY_APPLICATION);
    }
}
