package com.studymatching.study.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Locale;

public enum StudyDay {
    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY;

    @JsonCreator
    public static StudyDay parse(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (StudyDay day : values()) {
            if (normalized.equals(day.name()) || normalized.equals(day.name().substring(0, 3))) return day;
        }
        throw new IllegalArgumentException("Unknown day");
    }
}
