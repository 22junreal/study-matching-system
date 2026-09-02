package com.studymatching.studyapplication.exception;

public class StudyNotRecruitingException extends RuntimeException {

    public StudyNotRecruitingException() {
        super("현재 모집 중인 스터디가 아닙니다.");
    }
}