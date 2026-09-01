package com.studymatching.study.controller;

import com.studymatching.study.dto.StudyCreateRequest;
import com.studymatching.study.dto.StudyResponse;
import com.studymatching.study.service.StudyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/studies")
public class StudyController {

    private final StudyService studyService;

    public StudyController(StudyService studyService) {
        this.studyService = studyService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StudyResponse createStudy(
            Authentication authentication,
            @Valid @RequestBody StudyCreateRequest request
    ) {
        return studyService.createStudy(
                authentication.getName(),
                request
        );
    }

    @GetMapping("/{studyId}")
    public StudyResponse getStudy(
            @PathVariable Long studyId
    ) {
        return studyService.getStudy(studyId);
    }

    @GetMapping
    public List<StudyResponse> getStudies() {
        return studyService.getStudies();
    }
}