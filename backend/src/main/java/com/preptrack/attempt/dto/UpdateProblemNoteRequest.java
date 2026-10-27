package com.preptrack.attempt.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateProblemNoteRequest(@NotNull @Size(max = 20000) String insightNote) {}
