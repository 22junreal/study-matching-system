package com.studymatching.member.dto;

public record MemberResponse(
        Long id,
        String username,
        String email
) {
}