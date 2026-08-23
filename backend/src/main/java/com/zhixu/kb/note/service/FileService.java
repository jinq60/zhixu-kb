package com.zhixu.kb.note.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
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
    /** Xberg 专用短超时客户端，避免解析服务挂起时拖住上传请求线程 */
    private static final RestTemplate XBERG_REST_TEMPLATE = buildXbergRestTemplate();

    private static RestTemplate buildXbergRestTemplate() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(30000);
        return new RestTemplate(factory);
    }

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
            // 优先使用 Xberg 解析服务（保留表格/图片/标题结构），失败则回退到本地提取，避免两次提取
            ParsedDocument parsed = tryParseWithXberg(file, ext);
            if (parsed != null) {
                if (StringUtils.hasText(parsed.markdown)) {
                    extractedText = parsed.markdown;
                }
                if (StringUtils.hasText(parsed.html)) {
                    // Xberg HTML 同样过滤文档自带元信息/目录区/重复标题，避免干扰正文阅读
                    normalizedText = documentNormalizeService.filterNoiseLines(parsed.html);
                    // 从 HTML 提取纯文本作为 extractedText：
                    // 否则 extractedText 为空会落入本地 POI/PDFBox 分支，导致同一文档被解析两遍
                    String plainFromHtml = htmlToPlainText(parsed.html);
                    if (StringUtils.hasText(plainFromHtml)) {
                        extractedText = plainFromHtml;
                    }
                }
            }
            // Xberg 不可用或返回空：回退到本地提取
            if (!StringUtils.hasText(extractedText)) {
                try {
                    extractedText = extractText(file, ext);
                } catch (Exception ex) {
                    log.warn("Extract document text failed: name={} err={}", file.getOriginalFilename(), ex.getMessage());
                    extractedText = null;
                }
                if (extractedText != null && normalize) {
                    normalizedText = documentNormalizeService.normalize(extractedText);
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

        // 魔数校验：文件真实签名必须与扩展名一致，防止伪装成图片/PDF/docx 投毒下游解析链路
        try {
            validateMagicBytes(readHead(file, 16), ext);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文件读取失败");
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
            // 若笔记标题仍为默认名称，使用上传文件名作为标题（提升任务中心与页面展示体验）
            if (noteId != null) {
                try {
                    updateNoteTitleFromFileName(noteId, file.getOriginalFilename());
                } catch (Exception ex) {
                    log.warn("Update note title from file name failed: noteId={} err={}", noteId, ex.getMessage());
                }
            }
            return info;
        } catch (IOException e) {
            log.error("Save file failed: originalName={}, size={}, targetDir={}", file.getOriginalFilename(), file.getSize(), dir, e);
            throw new BusinessException(ResultCode.SERVER_ERROR, "保存文件失败");
        }
    }

    /**
     * 当笔记标题为默认空名时，用上传文件名（去掉扩展名）作为标题。
     */
    private void updateNoteTitleFromFileName(Long noteId, String fileName) {
        Note note = noteMapper.selectById(noteId);
        if (note == null) {
            return;
        }
        String title = note.getTitle();
        if (StringUtils.hasText(title)) {
            String trimmed = title.trim();
            if (!"新建笔记".equals(trimmed) && !"未命名笔记".equals(trimmed)) {
                return;
            }
        }
        String baseName = fileName;
        if (baseName != null) {
            int lastDot = baseName.lastIndexOf('.');
            if (lastDot > 0) {
                baseName = baseName.substring(0, lastDot);
            }
            baseName = baseName.trim();
        }
        if (!StringUtils.hasText(baseName)) {
            return;
        }
        note.setTitle(baseName);
        noteMapper.updateById(note);
        log.info("Note title updated from file name: noteId={} title={}", noteId, baseName);
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

        // 1) 先删数据库记录（数据源）：失败则中止，磁盘文件保持可访问，不会出现"记录指向已删除文件"的坏状态
        int rows = fileInfoMapper.deleteById(fileId);
        if (rows <= 0) {
            throw new BusinessException(ResultCode.SERVER_ERROR, "删除文件记录失败");
        }

        // 2) 删除物理文件（尽力而为：失败仅记录告警，孤儿文件由磁盘清理处理）
        try {
            Files.deleteIfExists(Paths.get(info.getFilePath()));
        } catch (IOException e) {
            log.warn("Delete local file failed (orphan left): id={}, path={}", fileId, info.getFilePath(), e);
        }

        // 3) 清理正文中的图片引用（尽力而为）
        if (info.getNoteId() != null) {
            try {
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
            } catch (Exception ex) {
                log.warn("Clean content img reference failed (best-effort): fileId={} err={}", fileId, ex.getMessage());
            }
        }
    }

    /**
     * 删除某笔记下的全部文件。顺序与 {@link #delete(Long)} 一致：先删数据库记录，
     * 物理文件延迟到事务提交后删除（尽力而为）——事务回滚时不会出现"记录还在、文件已丢"的悬空状态。
     */
    public void deleteByNoteId(Long noteId) {
        List<FileInfo> files = fileInfoMapper.selectList(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getNoteId, noteId));
        if (files == null || files.isEmpty()) {
            return;
        }
        int rows = fileInfoMapper.delete(new LambdaQueryWrapper<FileInfo>().eq(FileInfo::getNoteId, noteId));
        if (rows <= 0) {
            return;
        }
        Runnable physicalCleanup = () -> {
            for (FileInfo file : files) {
                try {
                    Files.deleteIfExists(Paths.get(file.getFilePath()));
                } catch (IOException e) {
                    log.warn("Cascade delete physical file failed: id={}, path={}", file.getId(), file.getFilePath());
                }
            }
        };
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            physicalCleanup.run();
                        }
                    });
        } else {
            physicalCleanup.run();
        }
    }

    /**
     * 分片暂存：写临时目录 chunk-tmp/{userId}/{identifier}/part-{index}。
     * 校验：identifier 仅允许字母数字与短横线（防路径穿越）、分片序号在合理范围、
     * 分片大小受限、分片目录按用户隔离（防止跨用户合并他人分片内容）。
     */
    public void storeChunk(MultipartFile file, String identifier, Integer chunkIndex, Integer totalChunks) {
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录");
        }
        if (identifier == null || !identifier.matches("^[A-Za-z0-9\\-_]{8,64}$")) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分片标识不合法");
        }
        if (chunkIndex == null || totalChunks == null
                || totalChunks < 1 || totalChunks > 512
                || chunkIndex < 0 || chunkIndex >= totalChunks) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分片序号不合法");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分片内容为空");
        }
        if (storageProperties.getMaxSize() != null && file.getSize() > storageProperties.getMaxSize()) {
            throw new BusinessException(ResultCode.PAYLOAD_TOO_LARGE, "单个分片超过大小限制");
        }
        // 累计配额：分片目录已有内容 + 当前分片不得超过单文件上限，
        // 防止在 merge 前通过海量分片占满磁盘（merge 时的总大小校验为最后一道防线）
        Path chunkDir = chunkTempDir(userId, identifier);
        long existingChunkBytes = sumExistingChunks(chunkDir);
        if (storageProperties.getMaxSize() != null
                && existingChunkBytes + file.getSize() > storageProperties.getMaxSize()) {
            deleteChunkTempDir(chunkDir);
            throw new BusinessException(ResultCode.PAYLOAD_TOO_LARGE, "文件超过大小限制");
        }
        try {
            Files.createDirectories(chunkDir);
            Path dest = chunkDir.resolve("part-" + chunkIndex);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("Store chunk failed: userId={} identifier={} index={}", userId, identifier, chunkIndex, e);
            throw new BusinessException(ResultCode.SERVER_ERROR, "分片保存失败");
        }
    }

    /** 统计分片目录中已有 part-* 文件的总字节数（目录不存在时为 0）。 */
    private long sumExistingChunks(Path chunkDir) {
        if (!Files.exists(chunkDir)) {
            return 0L;
        }
        try (java.util.stream.Stream<Path> paths = Files.list(chunkDir)) {
            return paths
                    .filter(p -> p.getFileName().toString().startsWith("part-"))
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0L;
                        }
                    })
                    .sum();
        } catch (IOException e) {
            return 0L;
        }
    }

    /**
     * 分片合并：校验片数齐全且总大小不超限后按序合并为临时文件，再走正常存储/解析流程。
     * 分片目录按用户隔离，防止合并他人分片内容。
     */
    public UploadPayload mergeAndStore(String identifier, String fileName, Integer totalChunks,
                                       Long noteId, boolean normalize) {
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录");
        }
        if (identifier == null || !identifier.matches("^[A-Za-z0-9\\-_]{8,64}$")) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分片标识不合法");
        }
        if (totalChunks == null || totalChunks < 1 || totalChunks > 512) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分片总数不合法");
        }
        Path chunkDir = chunkTempDir(userId, identifier);
        Path merged = chunkDir.resolve("merged.bin");
        try {
            long totalSize = 0L;
            for (int i = 0; i < totalChunks; i++) {
                Path part = chunkDir.resolve("part-" + i);
                if (!Files.exists(part)) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "分片不完整，缺少 part-" + i + "，请重传");
                }
                totalSize += Files.size(part);
            }
            // 合并前先做总大小校验，避免把超限内容整块读入内存
            if (storageProperties.getMaxSize() != null && totalSize > storageProperties.getMaxSize()) {
                deleteChunkTempDir(chunkDir);
                throw new BusinessException(ResultCode.PAYLOAD_TOO_LARGE, "文件超过大小限制");
            }
            Files.deleteIfExists(merged);
            try (OutputStream out = Files.newOutputStream(merged)) {
                for (int i = 0; i < totalChunks; i++) {
                    Files.copy(chunkDir.resolve("part-" + i), out);
                }
            }
            // 使用基于磁盘路径的 MultipartFile，避免合并后再整块读入内存
            String safeName = StringUtils.hasText(fileName) ? fileName : "upload.bin";
            MultipartFile multipartFile = new PathMultipartFile(merged, safeName,
                    mimeForImage(getExtension(safeName)), totalSize);
            try {
                return storeWithText(multipartFile, noteId, normalize);
            } finally {
                // 常规存储/解析完成后清理临时分片目录
                deleteChunkTempDir(chunkDir);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("Merge chunks failed: identifier={}", identifier, e);
            throw new BusinessException(ResultCode.SERVER_ERROR, "分片合并失败");
        }
    }

    /** 内存 MultipartFile 适配（合并后的完整文件进入常规上传流程） */
    private static final class ByteArrayMultipartFile implements MultipartFile {
        private final String originalName;
        private final byte[] bytes;
        private final String contentType;

        ByteArrayMultipartFile(String originalName, byte[] bytes, String contentType) {
            this.originalName = originalName;
            this.bytes = bytes;
            this.contentType = contentType;
        }

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return originalName;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return bytes == null || bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes == null ? 0 : bytes.length;
        }

        @Override
        public byte[] getBytes() throws IOException {
            return bytes;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            Files.write(dest.toPath(), bytes);
        }
    }

    /** 基于磁盘文件的 MultipartFile 适配：分片合并后不再整块读入内存 */
    private static final class PathMultipartFile implements MultipartFile {
        private final Path path;
        private final String originalName;
        private final String contentType;
        private final long size;

        PathMultipartFile(Path path, String originalName, String contentType, long size) {
            this.path = path;
            this.originalName = originalName;
            this.contentType = contentType;
            this.size = size;
        }

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return originalName;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return size == 0;
        }

        @Override
        public long getSize() {
            return size;
        }

        @Override
        public byte[] getBytes() throws IOException {
            return Files.readAllBytes(path);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return Files.newInputStream(path);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            Files.copy(path, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path chunkTempDir(Long userId, String identifier) {
        String root = StringUtils.hasText(storageProperties.getPath())
                ? storageProperties.getPath()
                : "./uploads/images/";
        return Paths.get(root).toAbsolutePath().normalize()
                .resolve("chunk-tmp")
                .resolve(String.valueOf(userId))
                .resolve(identifier);
    }

    private void deleteChunkTempDir(Path dir) {
        try {
            if (!Files.exists(dir)) {
                return;
            }
            // 递归删除整棵临时目录（含用户子目录/分片目录）
            try (java.util.stream.Stream<Path> paths = Files.walk(dir)) {
                paths.sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {
                            }
                        });
            }
        } catch (IOException ignored) {
        }
    }

    /**
     * 从已存储的文件解析纯文本（文档处理任务 PARSING 阶段使用）。
     * 图片/不支持类型返回 null。
     */
    public String parseTextFromStoredFile(Long fileId) {
        FileInfo info = fileInfoMapper.selectById(fileId);
        if (info == null) {
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(info.getFilePath()));
            String ext = getExtension(info.getOriginalName());
            if (ext == null || !DOC_EXTS.contains(ext)) {
                return null;
            }
            return extractTextFromBytes(bytes, ext);
        } catch (IOException e) {
            log.warn("Parse stored file failed: id={} err={}", fileId, e.getMessage());
            return null;
        }
    }

    private String extractTextFromBytes(byte[] bytes, String ext) {
        try {
            return extractText(new ByteArrayMultipartFile("doc." + ext, bytes,
                    mimeForImage(ext)), ext);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 启动清理：删除遗留的临时分片目录（上次异常中断残留）。
     */
    @javax.annotation.PostConstruct
    public void cleanupTempChunks() {
        try {
            String root = StringUtils.hasText(storageProperties.getPath())
                    ? storageProperties.getPath()
                    : "./uploads/images/";
            Path tmpDir = Paths.get(root).toAbsolutePath().normalize().resolve("chunk-tmp");
            if (Files.exists(tmpDir)) {
                try (java.util.stream.Stream<Path> dirs = Files.list(tmpDir)) {
                    dirs.forEach(this::deleteChunkTempDir);
                }
                log.info("Chunk temp dir cleaned: {}", tmpDir);
            }
        } catch (Exception ex) {
            log.warn("Cleanup chunk temp dir failed: {}", ex.getMessage());
        }
    }

    private String getExtension(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            return null;
        }
        String ext = filename.substring(filename.lastIndexOf('.') + 1);
        return ext.toLowerCase(Locale.ROOT);
    }

    private static String mimeForImage(String ext) {
        if (ext == null) {
            return "application/octet-stream";
        }
        switch (ext.toLowerCase(Locale.ROOT)) {
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

    private byte[] readHead(MultipartFile file, int length) throws IOException {
        try (InputStream in = file.getInputStream()) {
            byte[] buffer = new byte[length];
            int read = in.read(buffer);
            if (read <= 0) {
                return new byte[0];
            }
            byte[] result = new byte[read];
            System.arraycopy(buffer, 0, result, 0, read);
            return result;
        }
    }

    /**
     * 魔数校验：文件真实签名必须与扩展名一致。
     * 文本类文件（txt/md/markdown）无固定魔数，交由后续解析校验。
     */
    private void validateMagicBytes(byte[] head, String ext) {
        if (head == null || head.length == 0) {
            return;
        }
        String magic = detectMagicType(head);
        if (magic == null) {
            return;
        }
        String normalizedExt = ext == null ? "" : ext.toLowerCase(Locale.ROOT);
        boolean matched;
        switch (magic) {
            case "jpg":
                matched = "jpg".equals(normalizedExt) || "jpeg".equals(normalizedExt);
                break;
            case "png":
                matched = "png".equals(normalizedExt);
                break;
            case "pdf":
                matched = "pdf".equals(normalizedExt);
                break;
            case "zip":
                matched = "docx".equals(normalizedExt);
                break;
            default:
                matched = false;
        }
        if (!matched) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文件内容与扩展名不一致，已拒绝上传");
        }
    }

    private String detectMagicType(byte[] head) {
        int b0 = head[0] & 0xFF;
        int b1 = head.length > 1 ? head[1] & 0xFF : -1;
        int b2 = head.length > 2 ? head[2] & 0xFF : -1;
        int b3 = head.length > 3 ? head[3] & 0xFF : -1;
        if (b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF) {
            return "jpg";
        }
        if (b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47) {
            return "png";
        }
        if (b0 == 0x25 && b1 == 0x50 && b2 == 0x44 && b3 == 0x46) {
            return "pdf";
        }
        if (b0 == 0x50 && b1 == 0x4B && b2 == 0x03 && b3 == 0x04) {
            return "zip";
        }
        return null;
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
            ResponseEntity<Map> response = XBERG_REST_TEMPLATE.exchange(
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

    /**
     * 将 Xberg 返回的结构化 HTML 转为纯文本：块级标签转换行、行内标签剥除、常见实体解码。
     * 用于填充 extractedText，避免 Xberg 解析成功后再次触发本地 POI/PDFBox 提取。
     */
    private static String htmlToPlainText(String html) {
        String text = html.replaceAll("(?i)<(br|/p|/div|/h[1-6]|/li|/tr|/table)[^>]*>", "\n")
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                // &amp; 必须最后解码，避免 &amp;lt; 被二次解码为 <
                .replace("&amp;", "&");
        StringBuilder sb = new StringBuilder(text.length());
        boolean prevSpace = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                sb.append('\n');
                prevSpace = false;
            } else if (Character.isWhitespace(c)) {
                if (!prevSpace) {
                    sb.append(' ');
                }
                prevSpace = true;
            } else {
                sb.append(c);
                prevSpace = false;
            }
        }
        String result = sb.toString().trim();
        if (result.length() > MAX_EXTRACTED_CHARS) {
            result = result.substring(0, MAX_EXTRACTED_CHARS);
        }
        return result.isEmpty() ? null : result;
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
