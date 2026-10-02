package com.studymatching.study.exception;

import com.studymatching.common.exception.BusinessException;
import com.studymatching.common.exception.ErrorCode;

public class StudyCapacityBelowCurrentMembersException
        extends BusinessException {

    public StudyCapacityBelowCurrentMembersException() {
        super(ErrorCode.STUDY_CAPACITY_BELOW_CURRENT_MEMBERS);
    }
}
