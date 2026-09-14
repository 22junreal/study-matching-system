package com.studymatching.studyapplication.exception;

public class StudyApplicationCannotCancelException extends RuntimeException {

    public StudyApplicationCannotCancelException() {
        super("대기 중인 신청만 취소할 수 있습니다.");
    }
}