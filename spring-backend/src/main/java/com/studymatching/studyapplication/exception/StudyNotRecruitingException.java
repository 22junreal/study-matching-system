package com.studymatching.studyapplication.exception;

import com.studymatching.common.exception.BusinessException;
import com.studymatching.common.exception.ErrorCode;

public class StudyNotRecruitingException extends BusinessException {

    public StudyNotRecruitingException() {
        super(ErrorCode.STUDY_NOT_RECRUITING);
    }
}
