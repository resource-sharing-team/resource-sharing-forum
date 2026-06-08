package com.resourcesharing.forum.dto;

import com.resourcesharing.forum.domain.ResourceStatus;
import com.resourcesharing.forum.domain.ResourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class ResourceDtos {
    private ResourceDtos() {
    }

    public record ResourcePublishRequest(
            @NotBlank @Size(min = 5, max = 80) String title,
            @NotNull Long categoryId,
            @NotNull ResourceType type,
            @Size(min = 1, max = 5) List<String> tags,
            @NotBlank @Size(min = 20, max = 300) String summary,
            @NotBlank String description,
            String externalUrl
    ) {
    }

    public record ResourceView(
            Long id,
            String title,
            ResourceType type,
            ResourceStatus status,
            String summary,
            List<String> tags,
            int viewCount,
            int downloadCount,
            LocalDateTime publishedAt
    ) {
    }

    public record ReviewRequest(@NotNull Long resourceId, @NotNull Boolean approved, String reason) {
    }
}

