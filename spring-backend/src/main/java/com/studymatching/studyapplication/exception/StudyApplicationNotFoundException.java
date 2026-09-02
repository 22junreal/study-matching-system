package com.studymatching.studyapplication.exception;

public class StudyApplicationNotFoundException extends RuntimeException {

    public StudyApplicationNotFoundException() {
        super("참여 신청을 찾을 수 없습니다.");
    }
}