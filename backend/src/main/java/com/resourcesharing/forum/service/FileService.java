package com.resourcesharing.forum.service;

import com.resourcesharing.forum.dto.FileDtos.AttachmentView;
import com.resourcesharing.forum.dto.FileDtos.DownloadResult;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class FileService {

    public AttachmentView upload(MultipartFile file, Long resourceId) {
        return new AttachmentView(1L, resourceId, file.getOriginalFilename(), file.getContentType(), file.getSize(), "TEMP");
    }

    public DownloadResult download(Long id) {
        return new DownloadResult(1L, "demo.pdf", "/api/files/" + id + "/download/raw");
    }

    public List<AttachmentView> listByResource(Long resourceId) {
        return List.of();
    }
}

