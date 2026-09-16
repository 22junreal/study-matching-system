package com.studymatching.study.repository;

import com.studymatching.study.entity.Study;
import com.studymatching.study.entity.StudyCategory;
import com.studymatching.study.entity.StudyLevel;
import com.studymatching.study.entity.StudyStatus;
import org.springframework.data.jpa.domain.Specification;

public final class StudySpecification {

    private StudySpecification() {
    }

    public static Specification<Study> categoryEquals(
            StudyCategory category
    ) {
        return (root, query, criteriaBuilder) -> {

            if (category == null) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.equal(
                    root.get("category"),
                    category
            );
        };
    }

    public static Specification<Study> levelEquals(
            StudyLevel level
    ) {
        return (root, query, criteriaBuilder) -> {

            if (level == null) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.equal(
                    root.get("level"),
                    level
            );
        };
    }

    public static Specification<Study> statusEquals(
            StudyStatus status
    ) {
        return (root, query, criteriaBuilder) -> {

            if (status == null) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.equal(
                    root.get("status"),
                    status
            );
        };
    }
}