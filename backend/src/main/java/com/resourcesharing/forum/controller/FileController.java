package com.resourcesharing.forum.controller;

import com.resourcesharing.forum.common.ApiResponse;
import com.resourcesharing.forum.dto.FileDtos.AttachmentView;
import com.resourcesharing.forum.service.DesignSpecForumService;
import com.resourcesharing.forum.service.FileService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/files", "/api/v1/attachments"})
public class FileController {
    private final FileService fileService;
    private final DesignSpecForumService forumService;

    public FileController(FileService fileService, DesignSpecForumService forumService) {
        this.fileService = fileService;
        this.forumService = forumService;
    }

    @PostMapping("/upload")
    public ApiResponse<AttachmentView> upload(
            @RequestParam MultipartFile file,
            @RequestParam(required = false) Long resourceId,
            Authentication authentication
    ) {
        return ApiResponse.created(fileService.upload(file, resourceId, accountId(authentication)));
    }

    @GetMapping("/{id}/download")
    public ApiResponse<Map<String, Object>> download(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success(forumService.downloadAttachment(id, accountId(authentication)));
    }

    @GetMapping("/resources/{resourceId}")
    public ApiResponse<List<AttachmentView>> listByResource(@PathVariable Long resourceId) {
        return ApiResponse.success(fileService.listByResource(resourceId));
    }

    @PostMapping("/{id}/bind-resource/{resourceId}")
    public ApiResponse<AttachmentView> bindToResource(
            @PathVariable Long id,
            @PathVariable Long resourceId,
            Authentication authentication
    ) {
        return ApiResponse.success(fileService.bindToResource(id, resourceId, accountId(authentication)));
    }

    private static Long accountId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        try {
            return Long.parseLong(authentication.getName());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}

