package com.studymatching.profile.exception;

public class ProfileNotFoundException extends RuntimeException {

    public ProfileNotFoundException() {
        super("프로필이 존재하지 않습니다.");
    }
}