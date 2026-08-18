package com.zhixu.kb.note.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixu.kb.common.exception.BusinessException;
import com.zhixu.kb.common.result.ResultCode;
import com.zhixu.kb.common.utils.SecurityUtils;
import com.zhixu.kb.note.entity.Note;
import com.zhixu.kb.note.entity.NoteMindmap;
import com.zhixu.kb.note.entity.NoteStructure;
import com.zhixu.kb.note.mapper.NoteMapper;
import com.zhixu.kb.note.mapper.NoteMindmapMapper;
import com.zhixu.kb.note.mapper.NoteStructureMapper;
import com.zhixu.kb.note.model.NoteHistoryItem;
import com.zhixu.kb.note.model.NoteHistorySnapshot;
import com.zhixu.kb.system.entity.OperationLog;
import com.zhixu.kb.system.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoteHistoryService {

    private static final String REQUEST_URL_PREFIX = "/api/notes/";

    private final OperationLogMapper operationLogMapper;
    private final NoteMapper noteMapper;
    private final NoteStructureMapper noteStructureMapper;
    private final NoteMindmapMapper noteMindmapMapper;
    private final NoteStructureService noteStructureService;
    private final ObjectMapper objectMapper;

    public void recordNoteSnapshot(Long noteId, String operationType, String operationDesc, String requestUrl, Object requestParams) {
        try {
            Note note = findOwnNote(noteId);
            NoteHistorySnapshot snapshot = buildSnapshot(noteId, note);

            OperationLog logEntry = new OperationLog();
            logEntry.setUserId(note.getUserId());
            logEntry.setOperationType(operationType);
            logEntry.setOperationDesc(operationDesc);
            logEntry.setRequestMethod("SYSTEM");
            logEntry.setRequestUrl(StringUtils.hasText(requestUrl) ? requestUrl : REQUEST_URL_PREFIX + noteId);
            logEntry.setRequestParams(writeJsonQuietly(requestParams));
            logEntry.setResponseResult(writeJsonQuietly(snapshot));
            logEntry.setIpAddress("local");
            operationLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.warn("Record note snapshot failed: noteId={}, operationType={}", noteId, operationType, e);
        }
    }

    public List<NoteHistoryItem> list(Long noteId) {
        findOwnNote(noteId);
        String exactUrl = REQUEST_URL_PREFIX + noteId;
        String urlWithSlash = REQUEST_URL_PREFIX + noteId + "/";
        List<OperationLog> logs = operationLogMapper.selectList(new LambdaQueryWrapper<OperationLog>()
                .eq(OperationLog::getUserId, SecurityUtils.getUserId())
                .and(w -> w.eq(OperationLog::getRequestUrl, exactUrl)
                        .or()
                        .likeRight(OperationLog::getRequestUrl, urlWithSlash))
                .orderByDesc(OperationLog::getCreateTime)
                .orderByDesc(OperationLog::getId));

        List<NoteHistoryItem> items = new ArrayList<>();
        for (OperationLog logEntry : logs) {
            NoteHistorySnapshot snapshot = parseSnapshot(logEntry.getResponseResult());
            items.add(new NoteHistoryItem(
                    logEntry.getId(),
                    logEntry.getOperationType(),
                    logEntry.getOperationDesc(),
                    logEntry.getCreateTime(),
                    snapshot != null ? snapshot.getTitle() : null,
                    snapshot != null && StringUtils.hasText(snapshot.getOutlineJson()),
                    snapshot != null && StringUtils.hasText(snapshot.getMermaid())
            ));
        }
        return items;
    }

    @Transactional
    public void restore(Long noteId, Long historyId) {
        Note note = findOwnNote(noteId);
        OperationLog logEntry = operationLogMapper.selectById(historyId);
        String exactUrl = REQUEST_URL_PREFIX + noteId;
        String urlWithSlash = REQUEST_URL_PREFIX + noteId + "/";
        if (logEntry == null
                || !note.getUserId().equals(logEntry.getUserId())
                || !StringUtils.hasText(logEntry.getRequestUrl())
                || (!logEntry.getRequestUrl().equals(exactUrl) && !logEntry.getRequestUrl().startsWith(urlWithSlash))) {
            throw new BusinessException(ResultCode.NOT_FOUND, "History record not found");
        }

        NoteHistorySnapshot snapshot = parseSnapshot(logEntry.getResponseResult());
        if (snapshot == null) {
            throw new BusinessException(ResultCode.SERVER_ERROR, "History snapshot is invalid");
        }

        note.setTitle(snapshot.getTitle());
        note.setContent(snapshot.getContent());
        note.setOcrText(snapshot.getOcrText());
        note.setSummary(snapshot.getSummary());
        note.setKeywords(snapshot.getKeywords());
        note.setCoverImage(snapshot.getCoverImage());
        note.setCategoryId(snapshot.getCategoryId());
        note.setStatus(snapshot.getStatus());
        noteMapper.updateById(note);

        if (StringUtils.hasText(snapshot.getOutlineJson()) || StringUtils.hasText(snapshot.getMermaid())) {
            noteStructureService.saveStructure(noteId, snapshot.getOutlineJson(), snapshot.getMermaid());
        } else {
            noteStructureService.clearStructure(noteId);
        }

        recordNoteSnapshot(
                noteId,
                "NOTE_RESTORE",
                "Restore note from history #" + historyId,
                REQUEST_URL_PREFIX + noteId + "/history/" + historyId + "/restore",
                historyId
        );
    }

    private NoteHistorySnapshot buildSnapshot(Long noteId, Note note) {
        NoteHistorySnapshot snapshot = new NoteHistorySnapshot();
        snapshot.setNoteId(noteId);
        snapshot.setTitle(note.getTitle());
        snapshot.setContent(note.getContent());
        snapshot.setOcrText(note.getOcrText());
        snapshot.setSummary(note.getSummary());
        snapshot.setKeywords(note.getKeywords());
        snapshot.setCoverImage(note.getCoverImage());
        snapshot.setCategoryId(note.getCategoryId());
        snapshot.setStatus(note.getStatus());

        NoteStructure structure = noteStructureMapper.selectOne(new LambdaQueryWrapper<NoteStructure>()
                .eq(NoteStructure::getNoteId, noteId)
                .last("LIMIT 1"));
        NoteMindmap mindmap = noteMindmapMapper.selectOne(new LambdaQueryWrapper<NoteMindmap>()
                .eq(NoteMindmap::getNoteId, noteId)
                .last("LIMIT 1"));
        if (structure != null) {
            snapshot.setOutlineJson(structure.getOutlineJson());
        }
        if (mindmap != null) {
            snapshot.setMermaid(mindmap.getMapData());
        }
        return snapshot;
    }

    private String writeJsonQuietly(Object source) {
        try {
            return source == null ? null : objectMapper.writeValueAsString(source);
        } catch (Exception e) {
            log.warn("Write json quietly failed: {}", source, e);
            return null;
        }
    }

    private NoteHistorySnapshot parseSnapshot(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, NoteHistorySnapshot.class);
        } catch (Exception e) {
            log.warn("Parse history snapshot failed: {}", json, e);
            return null;
        }
    }

    private Note findOwnNote(Long noteId) {
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Unauthorized");
        }
        Note note = noteMapper.selectById(noteId);
        if (note == null || !userId.equals(note.getUserId()) || (note.getIsDeleted() != null && note.getIsDeleted() == 1)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Note not found");
        }
        return note;
    }
}
