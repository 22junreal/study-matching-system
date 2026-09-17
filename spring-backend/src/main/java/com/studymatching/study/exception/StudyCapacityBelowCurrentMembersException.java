package com.studymatching.study.exception;

public class StudyCapacityBelowCurrentMembersException
        extends RuntimeException {

    public StudyCapacityBelowCurrentMembersException() {
        super("현재 참여 인원보다 스터디 정원을 적게 설정할 수 없습니다.");
    }
}