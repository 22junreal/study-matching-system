package com.studymatching.study.exception;

import com.studymatching.common.exception.BusinessException;
import com.studymatching.common.exception.ErrorCode;

public class StudyAccessDeniedException extends BusinessException {

    public StudyAccessDeniedException() {
        super(ErrorCode.STUDY_ACCESS_DENIED);
    }
}
