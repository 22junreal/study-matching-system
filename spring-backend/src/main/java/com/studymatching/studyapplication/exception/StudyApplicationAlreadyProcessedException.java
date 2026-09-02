package com.studymatching.studyapplication.exception;

public class StudyApplicationAlreadyProcessedException
        extends RuntimeException {

    public StudyApplicationAlreadyProcessedException() {
        super("이미 처리된 참여 신청입니다.");
    }
}