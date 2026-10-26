package com.studymatching.studymember.dto;

import com.studymatching.studymember.entity.StudyMemberStatus;

public record StudyMemberResponse(Long memberId, String username, String role, StudyMemberStatus status) { }
