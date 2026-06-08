package com.resourcesharing.forum.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;

public final class RequestDtos {
    private RequestDtos() {
    }

    public record ResourceRequestCreateRequest(
            @NotBlank String title,
            @NotBlank String description,
            Long categoryId,
            List<String> tags,
            String expectedFormat,
            String rewardType,
            Integer rewardPoints,
            LocalDateTime deadlineAt
    ) {
    }

    public record AnswerCreateRequest(@NotBlank String content, Long resourceId, String externalUrl) {
    }
}

