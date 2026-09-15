package com.studymatching.studyapplication.controller;

import com.studymatching.studyapplication.dto.StudyApplicationResponse;
import com.studymatching.studyapplication.service.StudyApplicationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
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
    @GetMapping
    public List<StudyApplicationResponse> getApplications(
            @PathVariable Long studyId,
            Authentication authentication
    ) {
        return applicationService.getApplications(
                studyId,
                authentication.getName()
        );
    }
    @PatchMapping("/{applicationId}/approve")
    public StudyApplicationResponse approve(
            @PathVariable Long studyId,
            @PathVariable Long applicationId,
            Authentication authentication
    ) {
        return applicationService.approve(
                studyId,
                applicationId,
                authentication.getName()
        );
    }
    @PatchMapping("/{applicationId}/reject")
    public StudyApplicationResponse reject(
            @PathVariable Long studyId,
            @PathVariable Long applicationId,
            Authentication authentication
    ) {
        return applicationService.reject(
                studyId,
                applicationId,
                authentication.getName()
        );
    }
    @PatchMapping("/{applicationId}/cancel")
    public ResponseEntity<StudyApplicationResponse> cancel(
            @PathVariable Long studyId,
            @PathVariable Long applicationId,
            Authentication authentication
    ) {

        StudyApplicationResponse response =
                studyApplicationService.cancel(
                        studyId,
                        applicationId,
                        authentication.getName()
                );

        return ResponseEntity.ok(response);
    }
    @PatchMapping("/{applicationId}/reapply")
    public ResponseEntity<StudyApplicationResponse> reapply(
            @PathVariable Long studyId,
            @PathVariable Long applicationId,
            Authentication authentication
    ) {

        StudyApplicationResponse response =
                studyApplicationService.reapply(
                        studyId,
                        applicationId,
                        authentication.getName()
                );

        return ResponseEntity.ok(response);
    }
}