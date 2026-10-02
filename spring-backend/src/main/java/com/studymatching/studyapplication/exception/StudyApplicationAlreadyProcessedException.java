package com.studymatching.studyapplication.exception;

import com.studymatching.common.exception.BusinessException;
import com.studymatching.common.exception.ErrorCode;

public class StudyApplicationAlreadyProcessedException
        extends BusinessException {

    public StudyApplicationAlreadyProcessedException() {
        super(ErrorCode.STUDY_APPLICATION_ALREADY_PROCESSED);
    }
}
