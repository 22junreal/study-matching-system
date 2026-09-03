package com.studymatching.study.controller;

import com.studymatching.study.dto.StudyCreateRequest;
import com.studymatching.study.dto.StudyResponse;
import com.studymatching.study.service.StudyService;
import com.studymatching.study.dto.StudyUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.studymatching.study.dto.StudyPageResponse;
import com.studymatching.study.entity.StudyCategory;
import com.studymatching.study.entity.StudyLevel;
import com.studymatching.study.entity.StudyStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;

import java.util.List;


@RestController
@RequestMapping("/api/studies")
@Validated
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

    @GetMapping
    public StudyPageResponse getStudies(
            @RequestParam(required = false) StudyCategory category,
            @RequestParam(required = false) StudyLevel level,
            @RequestParam(required = false) StudyStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {

        if (category != null && level != null && status != null) {
            return studyService.searchStudies(
                    category,
                    level,
                    status,
                    page,
                    size
            );
        }

        return studyService.getStudies(page, size);
    }

    @GetMapping("/mine")
    public StudyPageResponse getMyStudies(
            Authentication authentication,
            @RequestParam(defaultValue = "0")
            @Min(0)
            int page,

            @RequestParam(defaultValue = "10")
            @Min(1)
            @Max(100)
            int size
    ) {
        return studyService.getMyStudies(
                authentication.getName(),
                page,
                size
        );
    }

    @GetMapping("/{studyId}")
    public StudyResponse getStudy(
            @PathVariable Long studyId
    ) {
        return studyService.getStudy(studyId);
    }

    @PutMapping("/{studyId}")
    public StudyResponse updateStudy(
            @PathVariable Long studyId,
            Authentication authentication,
            @Valid @RequestBody StudyUpdateRequest request
    ) {
        return studyService.updateStudy(
                studyId,
                authentication.getName(),
                request
        );
    }

    @DeleteMapping("/{studyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStudy(
            @PathVariable Long studyId,
            Authentication authentication
    ) {
        studyService.deleteStudy(
                studyId,
                authentication.getName()
        );
    }
}