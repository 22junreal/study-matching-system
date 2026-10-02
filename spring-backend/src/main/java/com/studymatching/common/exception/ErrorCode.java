package com.studymatching.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    DUPLICATE_USERNAME(HttpStatus.CONFLICT, "이미 사용 중인 사용자명입니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "프로필이 존재하지 않습니다."),
    STUDY_NOT_FOUND(HttpStatus.NOT_FOUND, "스터디를 찾을 수 없습니다."),
    STUDY_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 스터디에 대한 권한이 없습니다."),
    STUDY_INVALID_TIME_RANGE(HttpStatus.BAD_REQUEST, "스터디 시작 시간은 종료 시간보다 빨라야 합니다."),
    STUDY_CAPACITY_BELOW_CURRENT_MEMBERS(HttpStatus.CONFLICT, "현재 참여 인원보다 스터디 정원을 적게 설정할 수 없습니다."),
    OWN_STUDY_APPLICATION(HttpStatus.BAD_REQUEST, "본인이 만든 스터디에는 참여 신청할 수 없습니다."),
    DUPLICATE_STUDY_APPLICATION(HttpStatus.CONFLICT, "이미 해당 스터디에 참여 신청했습니다."),
    STUDY_NOT_RECRUITING(HttpStatus.CONFLICT, "현재 모집 중인 스터디가 아닙니다."),
    STUDY_APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "참여 신청을 찾을 수 없습니다."),
    STUDY_APPLICATION_ALREADY_PROCESSED(HttpStatus.CONFLICT, "이미 처리된 참여 신청입니다."),
    STUDY_CAPACITY_EXCEEDED(HttpStatus.CONFLICT, "스터디 정원이 이미 가득 찼습니다."),
    STUDY_APPLICATION_CANNOT_CANCEL(HttpStatus.CONFLICT, "대기 중인 신청만 취소할 수 있습니다."),
    STUDY_APPLICATION_CANNOT_REAPPLY(HttpStatus.CONFLICT, "취소된 신청만 재신청할 수 있습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
