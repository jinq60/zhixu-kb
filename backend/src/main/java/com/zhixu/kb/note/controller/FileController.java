package com.zhixu.kb.note.controller;

import com.zhixu.kb.common.result.Result;
import com.zhixu.kb.note.entity.FileInfo;
import com.zhixu.kb.note.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                              @RequestParam(value = "noteId", required = false) Long noteId,
                                              @RequestParam(value = "normalize", defaultValue = "true") Boolean normalize) {
        FileService.UploadPayload payload = fileService.storeWithText(file, noteId, Boolean.TRUE.equals(normalize));
        Map<String, Object> data = new HashMap<>();
        data.put("file", payload.getFile());
        data.put("extractedText", payload.getExtractedText());
        data.put("normalizedText", payload.getNormalizedText());
        return Result.success(data);
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<ByteArrayResource> content(@PathVariable Long id) {
        FileService.FileContent content = fileService.loadContent(id);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(content.getMimeType());
        } catch (Exception ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        ByteArrayResource resource = new ByteArrayResource(content.getBytes());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(content.getFilename()).build().toString())
                .body(resource);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        fileService.delete(id);
        return Result.success(null);
    }
}
