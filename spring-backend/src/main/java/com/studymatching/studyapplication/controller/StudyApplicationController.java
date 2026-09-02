package com.studymatching.studyapplication.controller;

import com.studymatching.studyapplication.dto.StudyApplicationResponse;
import com.studymatching.studyapplication.service.StudyApplicationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/studies/{studyId}/applications")
public class StudyApplicationController {

    private final StudyApplicationService applicationService;

    public StudyApplicationController(
            StudyApplicationService applicationService
    ) {
        this.applicationService = applicationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StudyApplicationResponse apply(
            @PathVariable Long studyId,
            Authentication authentication
    ) {
        return applicationService.apply(
                studyId,
                authentication.getName()
        );
    }
}