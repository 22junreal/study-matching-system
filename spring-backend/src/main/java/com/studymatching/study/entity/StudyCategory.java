package com.studymatching.study.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Locale;

public enum StudyCategory {
    PROGRAMMING,
    AI,
    DATA,
    LANGUAGE,
    CERTIFICATE,
    ETC;

    @JsonCreator
    public static StudyCategory parse(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
