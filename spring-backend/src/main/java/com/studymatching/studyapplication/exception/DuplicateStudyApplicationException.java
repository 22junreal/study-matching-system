package com.studymatching.studyapplication.exception;

public class DuplicateStudyApplicationException extends RuntimeException {

    public DuplicateStudyApplicationException() {
        super("이미 해당 스터디에 참여 신청했습니다.");
    }
}