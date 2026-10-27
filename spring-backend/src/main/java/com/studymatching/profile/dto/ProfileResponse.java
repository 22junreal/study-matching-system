package com.studymatching.profile.dto;
import com.studymatching.study.entity.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalTime;
import java.util.List;
public record ProfileResponse(Long id, String username, String department,
        StudyCategory category, StudyLevel level, List<StudyDay> days,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime) { }
