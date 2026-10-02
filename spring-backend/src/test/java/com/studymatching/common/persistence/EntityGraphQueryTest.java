package com.studymatching.common.persistence;

import com.studymatching.member.entity.Member;
import com.studymatching.member.repository.MemberRepository;
import com.studymatching.study.entity.Study;
import com.studymatching.study.entity.StudyCategory;
import com.studymatching.study.entity.StudyLevel;
import com.studymatching.study.repository.StudyRepository;
import com.studymatching.studyapplication.entity.StudyApplication;
import com.studymatching.studyapplication.repository.StudyApplicationRepository;
import com.studymatching.support.PostgresTestContainerConfig;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Import(PostgresTestContainerConfig.class)
class EntityGraphQueryTest {

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private StudyApplicationRepository applicationRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;
    private Member owner;

    @BeforeEach
    void setUp() {
        applicationRepository.deleteAll();
        studyRepository.deleteAll();
        memberRepository.deleteAll();

        owner = memberRepository.save(
                new Member("owner", "password", "owner@test.com")
        );

        statistics = entityManagerFactory
                .unwrap(SessionFactory.class)
                .getStatistics();
        statistics.setStatisticsEnabled(true);
    }

    @Test
    void studyPageLoadsOwnersWithoutNPlusOneQueries() {
        studyRepository.saveAll(List.of(
                createStudy("Study 1"),
                createStudy("Study 2"),
                createStudy("Study 3")
        ));
        statistics.clear();

        var page = studyRepository.findAll(PageRequest.of(0, 2));
        page.getContent().forEach(study -> study.getOwner().getUsername());

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    @Test
    void studySpecificationSearchLoadsOwnersWithoutNPlusOneQueries() {
        studyRepository.saveAll(List.of(
                createStudy("Study 1"),
                createStudy("Study 2"),
                createStudy("Study 3")
        ));
        statistics.clear();

        Specification<Study> allStudies = (root, query, builder) ->
                builder.conjunction();
        var page = studyRepository.findAll(
                allStudies,
                PageRequest.of(0, 2)
        );
        page.getContent().forEach(study -> study.getOwner().getUsername());

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    @Test
    void applicationListLoadsStudyAndApplicantInOneQuery() {
        Study study = studyRepository.save(createStudy("Java Study"));
        Member applicant1 = memberRepository.save(
                new Member("applicant1", "password", "applicant1@test.com")
        );
        Member applicant2 = memberRepository.save(
                new Member("applicant2", "password", "applicant2@test.com")
        );
        applicationRepository.saveAll(List.of(
                new StudyApplication(study, applicant1),
                new StudyApplication(study, applicant2)
        ));
        statistics.clear();

        var applications = applicationRepository
                .findByStudyIdOrderByCreatedAtAsc(study.getId());
        applications.forEach(application -> {
            application.getStudy().getTitle();
            application.getApplicant().getUsername();
        });

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    private Study createStudy(String title) {
        return new Study(
                owner,
                title,
                "Backend study",
                StudyCategory.PROGRAMMING,
                StudyLevel.BEGINNER,
                "MONDAY",
                LocalTime.of(19, 0),
                LocalTime.of(21, 0),
                4
        );
    }
}
