package com.studymatching.study.exception;

public class StudyInvalidTimeRangeException extends RuntimeException {

    public StudyInvalidTimeRangeException() {
        super("스터디 시작 시간은 종료 시간보다 빨라야 합니다.");
    }
}
