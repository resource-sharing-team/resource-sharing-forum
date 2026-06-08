package com.resourcesharing.forum.dto;

public final class FileDtos {
    private FileDtos() {
    }

    public record AttachmentView(Long id, Long resourceId, String fileName, String fileType, long fileSize, String status) {
    }

    public record DownloadResult(Long recordId, String fileName, String downloadUrl) {
    }
}

