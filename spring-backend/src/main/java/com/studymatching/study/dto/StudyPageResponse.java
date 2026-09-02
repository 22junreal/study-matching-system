package com.studymatching.study.dto;

import java.util.List;

public record StudyPageResponse(
        List<StudyResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}