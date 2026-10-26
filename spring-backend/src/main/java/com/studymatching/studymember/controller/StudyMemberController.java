package com.studymatching.studymember.controller;

import com.studymatching.studymember.dto.StudyMemberResponse;
import com.studymatching.studymember.dto.TransferOwnershipRequest;
import com.studymatching.studymember.service.StudyMemberService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/studies/{studyId}")
public class StudyMemberController {
    private final StudyMemberService service;
    public StudyMemberController(StudyMemberService service) { this.service = service; }

    @GetMapping("/members")
    public List<StudyMemberResponse> list(@PathVariable Long studyId, Authentication authentication) {
        return service.list(studyId, authentication.getName());
    }

    @DeleteMapping("/members/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@PathVariable Long studyId, Authentication authentication) {
        service.leave(studyId, authentication.getName());
    }

    @DeleteMapping("/members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long studyId, @PathVariable Long memberId, Authentication authentication) {
        service.remove(studyId, memberId, authentication.getName());
    }

    @PatchMapping("/owner")
    public StudyMemberResponse transfer(@PathVariable Long studyId, @Valid @RequestBody TransferOwnershipRequest request,
                                         Authentication authentication) {
        return service.transfer(studyId, request.memberId(), authentication.getName());
    }
}
