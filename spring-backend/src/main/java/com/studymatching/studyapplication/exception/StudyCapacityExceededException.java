package com.studymatching.studyapplication.exception;

public class StudyCapacityExceededException extends RuntimeException {

    public StudyCapacityExceededException() {
        super("스터디 정원이 이미 가득 찼습니다.");
    }
}