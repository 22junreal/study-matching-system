package com.studymatching.studymember.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TransferOwnershipRequest(@NotNull @Positive Long memberId) { }
