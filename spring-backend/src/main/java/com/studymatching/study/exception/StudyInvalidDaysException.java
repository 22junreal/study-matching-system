package com.studymatching.study.exception;
import com.studymatching.common.exception.BusinessException;
import com.studymatching.common.exception.ErrorCode;
public class StudyInvalidDaysException extends BusinessException {
    public StudyInvalidDaysException() { super(ErrorCode.STUDY_INVALID_DAYS); }
}
