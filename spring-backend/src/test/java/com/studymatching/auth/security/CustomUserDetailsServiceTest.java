package com.studymatching.auth.security;

import com.studymatching.member.entity.Member;
import com.studymatching.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class CustomUserDetailsServiceTest {

    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final CustomUserDetailsService userDetailsService =
            new CustomUserDetailsService(memberRepository);

    @Test
    void loadsSpringSecurityUserDetails() {
        Member member = new Member(
                "testuser",
                "encoded-password",
                "test@test.com"
        );
        given(memberRepository.findByUsername("testuser"))
                .willReturn(Optional.of(member));

        UserDetails result = userDetailsService.loadUserByUsername("testuser");

        assertThat(result.getUsername()).isEqualTo("testuser");
        assertThat(result.getPassword()).isEqualTo("encoded-password");
        assertThat(result.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_USER");
    }

    @Test
    void missingUserThrowsStandardSpringSecurityException() {
        given(memberRepository.findByUsername("missing"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("missing"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("사용자를 찾을 수 없습니다.");
    }
}
