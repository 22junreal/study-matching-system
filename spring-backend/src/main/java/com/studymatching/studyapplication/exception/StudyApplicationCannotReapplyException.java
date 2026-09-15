package com.studymatching.studyapplication.exception;

public class StudyApplicationCannotReapplyException extends RuntimeException {

    public StudyApplicationCannotReapplyException() {
        super("취소된 신청만 재신청할 수 있습니다.");
    }
}