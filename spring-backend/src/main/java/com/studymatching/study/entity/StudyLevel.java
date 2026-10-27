package com.studymatching.study.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Locale;

public enum StudyLevel {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED;

    @JsonCreator
    public static StudyLevel parse(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
