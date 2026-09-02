package com.studymatching.study.exception;

public class StudyAccessDeniedException extends RuntimeException {

    public StudyAccessDeniedException() {
        super("해당 스터디에 대한 권한이 없습니다.");
    }
}