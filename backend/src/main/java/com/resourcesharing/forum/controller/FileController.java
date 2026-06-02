package com.resourcesharing.forum.controller;

import com.resourcesharing.forum.common.ApiResponse;
import com.resourcesharing.forum.dto.FileDtos.AttachmentView;
import com.resourcesharing.forum.dto.FileDtos.DownloadResult;
import com.resourcesharing.forum.service.FileService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/upload")
    public ApiResponse<AttachmentView> upload(@RequestParam MultipartFile file, @RequestParam(required = false) Long resourceId) {
        return ApiResponse.success(fileService.upload(file, resourceId));
    }

    @GetMapping("/{id}/download")
    public ApiResponse<DownloadResult> download(@PathVariable Long id) {
        return ApiResponse.success(fileService.download(id));
    }

    @GetMapping("/resources/{resourceId}")
    public ApiResponse<List<AttachmentView>> listByResource(@PathVariable Long resourceId) {
        return ApiResponse.success(fileService.listByResource(resourceId));
    }
}

