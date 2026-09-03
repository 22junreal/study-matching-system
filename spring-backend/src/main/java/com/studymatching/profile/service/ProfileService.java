package com.studymatching.profile.service;

import com.studymatching.member.entity.Member;
import com.studymatching.member.repository.MemberRepository;
import com.studymatching.profile.dto.ProfileResponse;
import com.studymatching.profile.dto.ProfileUpdateRequest;
import com.studymatching.profile.entity.Profile;
import com.studymatching.profile.repository.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.studymatching.member.exception.MemberNotFoundException;
@Service
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final MemberRepository memberRepository;

    public ProfileService(
            ProfileRepository profileRepository,
            MemberRepository memberRepository
    ) {
        this.profileRepository = profileRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public ProfileResponse getMyProfile(String username) {

        Profile profile = profileRepository.findByMemberUsername(username)
                .orElseThrow(() ->
                        new IllegalArgumentException("프로필이 존재하지 않습니다.")
                );

        return toResponse(profile);
    }

    @Transactional
    public ProfileResponse updateMyProfile(
            String username,
            ProfileUpdateRequest request
    ) {

        Member member = memberRepository.findByUsername(username)
                .orElseThrow(MemberNotFoundException::new);

        Profile profile = profileRepository.findByMemberUsername(username)
                .orElseGet(() ->
                        profileRepository.save(new Profile(member))
                );

        profile.update(
                request.department(),
                request.preferredCategory(),
                request.level(),
                request.availableDays(),
                request.availableTime()
        );

        return toResponse(profile);
    }

    private ProfileResponse toResponse(Profile profile) {

        return new ProfileResponse(
                profile.getId(),
                profile.getMember().getUsername(),
                profile.getDepartment(),
                profile.getPreferredCategory(),
                profile.getLevel(),
                profile.getAvailableDays(),
                profile.getAvailableTime()
        );
    }
}