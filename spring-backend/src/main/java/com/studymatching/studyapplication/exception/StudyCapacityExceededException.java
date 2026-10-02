package com.studymatching.studyapplication.exception;

import com.studymatching.common.exception.BusinessException;
import com.studymatching.common.exception.ErrorCode;

public class StudyCapacityExceededException extends BusinessException {

    public StudyCapacityExceededException() {
        super(ErrorCode.STUDY_CAPACITY_EXCEEDED);
    }
}
