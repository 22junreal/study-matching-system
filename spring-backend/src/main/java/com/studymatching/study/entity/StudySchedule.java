package com.studymatching.study.entity;

import com.studymatching.study.exception.StudyInvalidTimeRangeException;
import com.studymatching.study.exception.StudyInvalidDaysException;
import java.time.LocalTime;
import java.util.*;

public final class StudySchedule {
    private StudySchedule() { }
    public static Set<StudyDay> days(List<StudyDay> days) {
        if (days == null || days.isEmpty()) return EnumSet.noneOf(StudyDay.class);
        if (days.stream().anyMatch(Objects::isNull) || new HashSet<>(days).size() != days.size()) throw new StudyInvalidDaysException();
        return EnumSet.copyOf(days);
    }
    public static void validateTime(LocalTime start, LocalTime end) {
        if ((start == null) != (end == null)
                || (start != null && (!start.isBefore(end) || start.getSecond() != 0 || end.getSecond() != 0
                || start.getNano() != 0 || end.getNano() != 0))) throw new StudyInvalidTimeRangeException();
    }
}
