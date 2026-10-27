package com.preptrack.attempt.dto;

import java.util.List;

public record AttemptHistoryPage(List<AttemptHistoryDto> content, int page, int size, long totalElements,
        int totalPages, boolean last) {}
