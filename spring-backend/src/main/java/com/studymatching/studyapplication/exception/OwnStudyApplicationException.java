package com.studymatching.studyapplication.exception;

public class OwnStudyApplicationException extends RuntimeException {

    public OwnStudyApplicationException() {
        super("본인이 만든 스터디에는 참여 신청할 수 없습니다.");
    }
}