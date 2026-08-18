package com.zhixu.kb.note.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.zhixu.kb.common.exception.BusinessException;
import com.zhixu.kb.common.result.ResultCode;
import com.zhixu.kb.common.utils.SecurityUtils;
import com.zhixu.kb.note.config.FileStorageProperties;
import com.zhixu.kb.note.entity.FileInfo;
import com.zhixu.kb.note.entity.Note;
import com.zhixu.kb.note.mapper.FileInfoMapper;
import com.zhixu.kb.note.mapper.NoteMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 文件服务：图片/文档上传（文档自动提取文本，进入个人知识库）、读取与删除。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FileService {

    /** 可做 OCR 识别的图片类型 */
    public static final Set<String> IMAGE_EXTS = new HashSet<>(Arrays.asList("jpg", "jpeg", "png"));

    /** 可直接提取文本的文档类型 */
    public static final Set<String> DOC_EXTS = new HashSet<>(Arrays.asList("txt", "md", "markdown", "pdf", "docx"));

    /** 文本提取结果的最大字节数，防止超大文档撑爆笔记字段 */
    private static final int MAX_EXTRACTED_CHARS = 200_000;

    private final FileStorageProperties storageProperties;
    private final FileInfoMapper fileInfoMapper;
    private final NoteMapper noteMapper;
    private final DocumentNormalizeService documentNormalizeService;
    private final RestTemplate restTemplate;

    @Value("${PDF_PARSE_SERVICE_URL:}")
    private String pdfParseServiceUrl;

    /**
     * 上传并存储文件；文档类型附带提取的纯文本（供前端直接作为笔记素材）。
     */
    public UploadPayload storeWithText(MultipartFile file, Long noteId) {
        return storeWithText(file, noteId, true);
    }

    /**
     * 上传并存储文件；文档类型附带提取文本与 AI 规范化文本。
     *
     * @param normalize 是否对文档文本做规范化（AI 优先、规则降级）
     */
    public UploadPayload storeWithText(MultipartFile file, Long noteId, boolean normalize) {
        FileInfo info = store(file, noteId);
        String ext = getExtension(file.getOriginalFilename());
        String extractedText = null;
        String normalizedText = null;
        if (ext != null && DOC_EXTS.contains(ext)) {
            try {
                extractedText = extractText(file, ext);
            } catch (Exception ex) {
                log.warn("Extract document text failed: name={} err={}", file.getOriginalFilename(), ex.getMessage());
                extractedText = null;
            }
            if (extractedText != null && normalize) {
                normalizedText = documentNormalizeService.normalize(extractedText);
            }

            // 优先使用 Xberg 解析服务（保留表格/图片/标题结构），失败则回退到本地提取
            ParsedDocument parsed = tryParseWithXberg(file, ext);
            if (parsed != null) {
                if (StringUtils.hasText(parsed.markdown)) {
                    extractedText = parsed.markdown;
                }
                if (StringUtils.hasText(parsed.html)) {
                    normalizedText = parsed.html;
                }
            }
        }
        return new UploadPayload(info, extractedText, normalizedText);
    }

    public FileInfo store(MultipartFile file, Long noteId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文件不能为空");
        }
        if (storageProperties.getMaxSize() != null && file.getSize() > storageProperties.getMaxSize()) {
            throw new BusinessException(ResultCode.PAYLOAD_TOO_LARGE, "文件超过大小限制");
        }

        // Validate noteId ownership to prevent attaching files to other users' notes
        if (noteId != null) {
            Long userId = SecurityUtils.getUserId();
            if (userId == null) {
                throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录");
            }
            Note note = noteMapper.selectById(noteId);
            if (note == null || !userId.equals(note.getUserId())
                    || (note.getIsDeleted() != null && note.getIsDeleted() == 1)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权操作该笔记");
            }
        }

        String ext = getExtension(file.getOriginalFilename());
        String contentType = file.getContentType();
        if (storageProperties.getAllowedTypes() != null
                && !storageProperties.getAllowedTypes().isEmpty()) {
            boolean allowedByExt = ext != null && storageProperties.getAllowedTypes().stream()
                    .anyMatch(type -> type.equalsIgnoreCase(ext));
            boolean allowedByMime = contentType != null && storageProperties.getAllowedTypes().stream()
                    .anyMatch(type -> contentType.toLowerCase(Locale.ROOT).contains(type.toLowerCase(Locale.ROOT)));
            if (!allowedByExt && !allowedByMime) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的文件类型（无扩展名或 MIME 类型异常文件一律拒绝）");
            }
            // 图片额外校验 MIME 与扩展名一致，防止重命名绕过
            if (ext != null && IMAGE_EXTS.contains(ext.toLowerCase(Locale.ROOT))) {
                if (!isImageMimeMatching(ext, contentType)) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "图片扩展名与 MIME 类型不一致");
                }
            }
        }

        String storedName = IdWorker.get32UUID() + (ext != null ? "." + ext : "");
        String rootPath = StringUtils.hasText(storageProperties.getPath())
                ? storageProperties.getPath()
                : "./uploads/images/";
        Path dir = Paths.get(rootPath).toAbsolutePath().normalize();

        try {
            Files.createDirectories(dir);
            Path dest = dir.resolve(storedName);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
            }

            FileInfo info = new FileInfo();
            info.setNoteId(noteId);
            info.setOriginalName(file.getOriginalFilename());
            info.setStoredName(storedName);
            info.setFilePath(dest.toString());
            info.setFileSize(file.getSize());
            info.setMimeType(file.getContentType());
            fileInfoMapper.insert(info);
            return info;
        } catch (IOException e) {
            log.error("Save file failed: originalName={}, size={}, targetDir={}", file.getOriginalFilename(), file.getSize(), dir, e);
            throw new BusinessException(ResultCode.SERVER_ERROR, "保存文件失败");
        }
    }

    /**
     * 按文档类型提取纯文本：txt/md 直接读取，docx 用 POI，pdf 用 PDFBox。
     */
    private String extractText(MultipartFile file, String ext) throws IOException {
        byte[] bytes = file.getBytes();
        String text;
        switch (ext) {
            case "txt":
            case "md":
            case "markdown":
                text = readUtf8(bytes);
                break;
            case "docx":
                text = readDocx(bytes);
                break;
            case "pdf":
                text = readPdf(bytes);
                break;
            default:
                return null;
        }
        if (text == null) {
            return null;
        }
        text = text.replace("\\n", "\n")
                .replace("\\r", "\n")
                .replace("\\t", "\t")
                .replace("\r\n", "\n")
                .trim();
        if (text.length() > MAX_EXTRACTED_CHARS) {
            text = text.substring(0, MAX_EXTRACTED_CHARS);
        }
        return text;
    }

    private String readUtf8(byte[] bytes) {
        if (bytes.length >= 3
                && bytes[0] == (byte) 0xEF
                && bytes[1] == (byte) 0xBB
                && bytes[2] == (byte) 0xBF) {
            bytes = Arrays.copyOfRange(bytes, 3, bytes.length);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private String readDocx(byte[] bytes) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String readPdf(byte[] bytes) throws IOException {
        try (PDDocument document = PDDocument.load(bytes)) {
            if (!document.isEncrypted()) {
                PDFTextStripper stripper = new PDFTextStripper();
                return stripper.getText(document);
            }
            return null;
        } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException ex) {
            return null;
        }
    }

    public FileInfo findOwnFile(Long fileId) {
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录");
        }

        FileInfo info = fileInfoMapper.selectById(fileId);
        if (info == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文件不存在");
        }
        if (info.getNoteId() == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "文件未绑定笔记，禁止访问");
        }

        Note note = noteMapper.selectById(info.getNoteId());
        if (note == null || !userId.equals(note.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该文件");
        }
        return info;
    }

    public FileInfo findReadableFile(Long fileId) {
        FileInfo info = fileInfoMapper.selectById(fileId);
        if (info == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文件不存在");
        }
        if (info.getNoteId() == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "文件未绑定笔记，禁止访问");
        }

        Note note = noteMapper.selectById(info.getNoteId());
        if (note == null || (note.getIsDeleted() != null && note.getIsDeleted() == 1)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "笔记不存在");
        }

        Long currentUserId = SecurityUtils.getUserId();
        boolean ownNote = currentUserId != null && currentUserId.equals(note.getUserId());
        boolean published = note.getStatus() != null && note.getStatus() == 1;
        if (!ownNote && !published) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该文件");
        }
        return info;
    }

    public FileContent loadContent(Long fileId) {
        FileInfo info = findReadableFile(fileId);
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(info.getFilePath()));
            // 仅图片允许按客户端声明的 mime inline 渲染；其余一律 octet-stream，杜绝 HTML 嗅探/存储型 XSS
            String mimeType = "application/octet-stream";
            String ext = getExtension(info.getOriginalName());
            if (ext != null && IMAGE_EXTS.contains(ext.toLowerCase())) {
                mimeType = mimeForImage(ext);
            }
            String filename = StringUtils.hasText(info.getOriginalName())
                    ? info.getOriginalName()
                    : info.getStoredName();
            return new FileContent(bytes, mimeType, filename);
        } catch (IOException e) {
            log.error("Read file failed: id={}, path={}", fileId, info.getFilePath(), e);
            throw new BusinessException(ResultCode.SERVER_ERROR, "读取文件失败");
        }
    }

    public void delete(Long fileId) {
        FileInfo info = findOwnFile(fileId);

        // 1) 先删除物理文件，失败则中止，避免数据库记录已删但磁盘遗留孤儿文件
        try {
            Files.deleteIfExists(Paths.get(info.getFilePath()));
        } catch (IOException e) {
            log.error("Delete local file failed: id={}, path={}", fileId, info.getFilePath(), e);
            throw new BusinessException(ResultCode.SERVER_ERROR, "删除物理文件失败");
        }

        // 2) 清理正文中的图片引用
        if (info.getNoteId() != null) {
            Note note = noteMapper.selectById(info.getNoteId());
            if (note != null && StringUtils.hasText(note.getContent())) {
                String pattern = "<img[^>]*src=[\"'][^\"']*?/api/files/" + fileId + "/content[^\"']*[\"'][^>]*/?>";
                String cleaned = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
                        .matcher(note.getContent())
                        .replaceAll("");
                if (!cleaned.equals(note.getContent())) {
                    note.setContent(cleaned);
                    noteMapper.updateById(note);
                }
            }
        }

        // 3) 最后删除数据库记录
        int rows = fileInfoMapper.deleteById(fileId);
        if (rows <= 0) {
            throw new BusinessException(ResultCode.SERVER_ERROR, "删除文件记录失败");
        }
    }

    private String getExtension(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            return null;
        }
        String ext = filename.substring(filename.lastIndexOf('.') + 1);
        return ext.toLowerCase(Locale.ROOT);
    }

    private String mimeForImage(String ext) {
        switch (ext) {
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            default:
                return "application/octet-stream";
        }
    }

    private boolean isImageMimeMatching(String ext, String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return false;
        }
        String mime = contentType.toLowerCase(Locale.ROOT);
        if (ext.equalsIgnoreCase("png")) {
            return mime.contains("png");
        }
        if (ext.equalsIgnoreCase("jpg") || ext.equalsIgnoreCase("jpeg")) {
            return mime.contains("jpeg") || mime.contains("jpg");
        }
        return false;
    }

    /**
     * 调用 Xberg PDF/Office 解析服务获取 HTML。
     * 服务不可用时静默回退，不影响原有本地提取逻辑。
     */
    private ParsedDocument tryParseWithXberg(MultipartFile file, String ext) {
        if (!StringUtils.hasText(pdfParseServiceUrl)) {
            return null;
        }
        if (!"pdf".equalsIgnoreCase(ext) && !"docx".equalsIgnoreCase(ext)) {
            return null;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("files", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            });
            body.add("output_format", "html");

            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    pdfParseServiceUrl + "/extract",
                    HttpMethod.POST,
                    entity,
                    Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return null;
            }
            Map<String, Object> result = response.getBody();
            Object resultsObj = result.get("results");
            if (!(resultsObj instanceof java.util.List) || ((java.util.List<?>) resultsObj).isEmpty()) {
                return null;
            }
            Object first = ((java.util.List<?>) resultsObj).get(0);
            if (!(first instanceof Map)) {
                return null;
            }
            Map<?, ?> doc = (Map<?, ?>) first;
            Object contentObj = doc.get("content");
            String html = contentObj instanceof String ? (String) contentObj : null;
            if (!StringUtils.hasText(html)) {
                return null;
            }
            Object metadata = doc.get("metadata");
            Object pageCount = metadata instanceof Map ? ((Map<?, ?>) metadata).get("page_count") : null;
            log.info("Xberg parsed document: name={}, pages={}",
                    file.getOriginalFilename(), pageCount);
            return new ParsedDocument(null, html);
        } catch (Exception ex) {
            log.warn("Xberg parse service unavailable, fallback to local extraction: {}", ex.getMessage());
            return null;
        }
    }

    private static class ParsedDocument {
        private final String markdown;
        private final String html;

        ParsedDocument(String markdown, String html) {
            this.markdown = markdown;
            this.html = html;
        }
    }

    /**
     * 上传结果：文件记录 + 文档提取文本 + AI 规范化文本（图片均为 null）。
     */
    public static class UploadPayload {
        private final FileInfo file;
        private final String extractedText;
        private final String normalizedText;

        public UploadPayload(FileInfo file, String extractedText, String normalizedText) {
            this.file = file;
            this.extractedText = extractedText;
            this.normalizedText = normalizedText;
        }

        public FileInfo getFile() {
            return file;
        }

        public String getExtractedText() {
            return extractedText;
        }

        public String getNormalizedText() {
            return normalizedText;
        }
    }

    public static class FileContent {
        private final byte[] bytes;
        private final String mimeType;
        private final String filename;

        public FileContent(byte[] bytes, String mimeType, String filename) {
            this.bytes = bytes;
            this.mimeType = mimeType;
            this.filename = filename;
        }

        public byte[] getBytes() {
            return bytes;
        }

        public String getMimeType() {
            return mimeType;
        }

        public String getFilename() {
            return filename;
        }
    }
}
