package com.zhixu.kb.graph.service;

import com.zhixu.kb.common.exception.BusinessException;
import com.zhixu.kb.common.result.ResultCode;
import com.zhixu.kb.common.utils.SecurityUtils;
import com.zhixu.kb.graph.Neo4jAccessor;
import com.zhixu.kb.graph.model.GraphBuildResult;
import com.zhixu.kb.graph.model.GraphData;
import com.zhixu.kb.graph.model.GraphEdge;
import com.zhixu.kb.graph.model.GraphNode;
import com.zhixu.kb.graph.model.GraphOverview;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixu.kb.note.entity.Note;
import com.zhixu.kb.note.mapper.NoteMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/**
 * 知识图谱服务：构建（抽取 + 写入 Neo4j）、查询、搜索、删除、总览。
 * 图谱不可用时所有操作返回可用性提示，不影响主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphService {

    /** 分类/全局批量构建的笔记数量上限：超出建议分批构建，防止请求线程被长时间占用 */
    private static final int MAX_BATCH_BUILD_NOTES = 20;

    private final Neo4jAccessor neo4jAccessor;
    private final GraphExtractionService extractionService;
    private final NoteMapper noteMapper;
    /** 每用户同时只能有一个批量构建任务 */
    private final Map<Long, Boolean> buildingUsers = new ConcurrentHashMap<>();

    public GraphBuildResult build(Long noteId) {
        Long userId = SecurityUtils.getUserId();
        Note note = noteMapper.selectById(noteId);
        if (userId == null || note == null || !userId.equals(note.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "笔记不存在");
        }

        if (!neo4jAccessor.isAvailable()) {
            GraphBuildResult result = new GraphBuildResult();
            result.setNoteId(noteId);
            result.setMessage("图谱服务（Neo4j）不可用，请检查 Neo4j 配置后重试");
            return result;
        }

        String content = note.getContent();
        if (!StringUtils.hasText(content)) {
            content = note.getOcrText();
        }
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "笔记内容为空，无法构建图谱");
        }

        Map<String, Object> extraction = extractionService.extract(content);
        List<GraphNode> entities = castEntities(extraction.get("entities"));
        List<GraphEdge> relations = castEdges(extraction.get("relations"));
        String source = String.valueOf(extraction.getOrDefault("source", "ai"));

        boolean persisted = persistGraph(note, entities, relations);

        GraphBuildResult result = new GraphBuildResult();
        result.setNoteId(noteId);
        result.setEntityCount(entities.size());
        result.setRelationCount(relations.size());
        result.setExtractionSource(source);
        result.setMessage(persisted
                ? "图谱构建成功（" + entities.size() + " 个实体，" + relations.size() + " 条关系）"
                : "抽取完成但写入 Neo4j 失败，请检查 Neo4j 服务");
        return result;
    }

    public GraphData get(Long noteId) {
        Long userId = SecurityUtils.getUserId();
        Note note = noteMapper.selectById(noteId);
        if (userId == null || note == null || !userId.equals(note.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "笔记不存在");
        }
        if (!neo4jAccessor.isAvailable()) {
            GraphData data = new GraphData();
            data.setNoteId(noteId);
            data.setNoteTitle(note.getTitle());
            data.setNodes(new ArrayList<>());
            data.setEdges(new ArrayList<>());
            return data;
        }

        Map<String, Object> params = new HashMap<>();
        params.put("noteId", "note-" + noteId);
        params.put("userId", String.valueOf(userId));

        List<GraphNode> nodes = neo4jAccessor.read(
                "MATCH (n:Note {id: $noteId})-[:CONTAINS]->(e:Entity) " +
                        "RETURN e.name AS name, e.type AS type, e.description AS description ORDER BY e.name",
                params,
                (tx, cypher, p) -> {
                    List<GraphNode> out = new ArrayList<>();
                    Result result = tx.run(cypher, p);
                    while (result.hasNext()) {
                        Record record = result.next();
                        GraphNode node = new GraphNode();
                        node.setId(record.get("name").asString());
                        node.setName(record.get("name").asString());
                        node.setType(record.get("type").asString("其他"));
                        node.setDescription(record.get("description").asString(""));
                        out.add(node);
                    }
                    return out;
                });

        List<GraphEdge> edges = neo4jAccessor.read(
                "MATCH (n:Note {id: $noteId})-[:CONTAINS]->(a:Entity), " +
                        "(n)-[:CONTAINS]->(b:Entity), (a)-[r:RELATED]->(b) " +
                        "RETURN a.name AS source, b.name AS target, r.relation AS relation",
                params,
                (tx, cypher, p) -> {
                    List<GraphEdge> out = new ArrayList<>();
                    Result result = tx.run(cypher, p);
                    while (result.hasNext()) {
                        Record record = result.next();
                        GraphEdge edge = new GraphEdge();
                        edge.setSource(record.get("source").asString());
                        edge.setTarget(record.get("target").asString());
                        edge.setRelation(record.get("relation").asString());
                        out.add(edge);
                    }
                    return out;
                });

        List<GraphEdge> noteEdges = new ArrayList<>();
        if (nodes != null && !nodes.isEmpty()) {
            GraphNode noteNode = new GraphNode();
            noteNode.setId("note-" + noteId);
            noteNode.setName(note.getTitle() == null ? "未命名笔记" : note.getTitle());
            noteNode.setType("笔记");
            noteNode.setDescription("");
            nodes.add(0, noteNode);
            for (GraphNode entity : nodes.subList(1, nodes.size())) {
                GraphEdge edge = new GraphEdge();
                edge.setSource(noteNode.getId());
                edge.setTarget(entity.getId());
                edge.setRelation("包含");
                noteEdges.add(edge);
            }
        }
        if (edges != null) {
            edges.addAll(noteEdges);
        } else {
            edges = noteEdges;
        }

        GraphData data = new GraphData();
        data.setNoteId(noteId);
        data.setNoteTitle(note.getTitle());
        data.setNodes(nodes == null ? new ArrayList<>() : nodes);
        data.setEdges(edges);
        data.setExtractionSource("stored");
        return data;
    }

    public GraphBuildResult buildCategory(Long categoryId) {
        return buildCategory(categoryId, () -> false);
    }

    /**
     * 批量构建分类/全局图谱。
     * @param cancelSignal 取消信号（如任务被用户删除），在每篇笔记处理前检查，
     *                     触发后返回已完成的部分结果而非抛异常
     */
    public GraphBuildResult buildCategory(Long categoryId, BooleanSupplier cancelSignal) {
        Long userId = SecurityUtils.getUserId();
        if (buildingUsers.putIfAbsent(userId, Boolean.TRUE) != null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "已有图谱构建任务进行中，请稍后再试");
        }
        try {
            return doBuildCategory(userId, categoryId, cancelSignal);
        } finally {
            buildingUsers.remove(userId);
        }
    }

    private GraphBuildResult doBuildCategory(Long userId, Long categoryId, BooleanSupplier cancelSignal) {
        LambdaQueryWrapper<Note> wrapper = new LambdaQueryWrapper<Note>()
                .eq(Note::getUserId, userId)
                .eq(categoryId != null, Note::getCategoryId, categoryId)
                .orderByAsc(Note::getId);
        List<Note> notes = noteMapper.selectList(wrapper);
        if (notes == null || notes.isEmpty()) {
            GraphBuildResult result = new GraphBuildResult();
            result.setMessage(categoryId == null ? "当前账号下没有笔记" : "该分类下没有笔记");
            return result;
        }
        if (notes.size() > MAX_BATCH_BUILD_NOTES) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "一次最多构建 " + MAX_BATCH_BUILD_NOTES + " 篇笔记的图谱，当前 " + notes.size()
                            + " 篇，请先按分类分批构建");
        }

        int totalEntities = 0;
        int totalRelations = 0;
        int processed = 0;
        for (Note note : notes) {
            if (cancelSignal.getAsBoolean()) {
                GraphBuildResult cancelled = new GraphBuildResult();
                cancelled.setMessage("构建已取消，已完成 " + processed + "/" + notes.size() + " 篇笔记");
                cancelled.setEntityCount(totalEntities);
                cancelled.setRelationCount(totalRelations);
                return cancelled;
            }
            String content = StringUtils.hasText(note.getContent()) ? note.getContent() : note.getOcrText();
            if (!StringUtils.hasText(content)) {
                continue;
            }
            Map<String, Object> extraction = extractionService.extract(content);
            List<GraphNode> entities = castEntities(extraction.get("entities"));
            List<GraphEdge> relations = castEdges(extraction.get("relations"));
            persistGraph(note, entities, relations);
            totalEntities += entities.size();
            totalRelations += relations.size();
            processed++;
        }

        GraphBuildResult result = new GraphBuildResult();
        result.setMessage("分类体系图谱构建完成，共抽取 " + totalEntities + " 个实体、" + totalRelations + " 条关系，覆盖 " + notes.size() + " 篇笔记");
        result.setEntityCount(totalEntities);
        result.setRelationCount(totalRelations);
        result.setExtractionSource("ai");
        return result;
    }

    public GraphBuildResult buildGlobal() {
        return buildCategory(null);
    }

    public GraphData getByCategory(Long categoryId) {
        Long userId = SecurityUtils.getUserId();
        if (!neo4jAccessor.isAvailable()) {
            GraphData data = new GraphData();
            data.setNoteTitle(categoryId == null ? "全局知识体系" : "分类知识体系");
            data.setNodes(new ArrayList<>());
            data.setEdges(new ArrayList<>());
            return data;
        }

        String categoryFilter = categoryId == null ? "" : " AND n.categoryId = $categoryId ";
        Map<String, Object> params = new HashMap<>();
        params.put("userId", String.valueOf(userId));
        params.put("categoryId", categoryId == null ? "" : String.valueOf(categoryId));

        List<GraphNode> noteNodes = neo4jAccessor.read(
                "MATCH (n:Note {userId: $userId}) " +
                        "WHERE true " + categoryFilter +
                        "RETURN n.id AS id, n.title AS title ORDER BY n.title",
                params,
                (tx, cypher, p) -> {
                    List<GraphNode> out = new ArrayList<>();
                    Result result = tx.run(cypher, p);
                    while (result.hasNext()) {
                        Record record = result.next();
                        GraphNode node = new GraphNode();
                        node.setId(record.get("id").asString());
                        node.setName(record.get("title").asString("未命名笔记"));
                        node.setType("笔记");
                        node.setDescription("");
                        out.add(node);
                    }
                    return out;
                });

        List<GraphNode> entityNodes = neo4jAccessor.read(
                "MATCH (n:Note {userId: $userId})-[:CONTAINS]->(e:Entity) " +
                        "WHERE true " + categoryFilter +
                        "RETURN e.name AS name, e.type AS type, e.description AS description, count(DISTINCT n.id) AS noteCount " +
                        "ORDER BY noteCount DESC, e.name LIMIT 200",
                params,
                (tx, cypher, p) -> {
                    List<GraphNode> out = new ArrayList<>();
                    Result result = tx.run(cypher, p);
                    while (result.hasNext()) {
                        Record record = result.next();
                        GraphNode node = new GraphNode();
                        node.setId(record.get("name").asString());
                        node.setName(record.get("name").asString());
                        node.setType(record.get("type").asString("其他"));
                        node.setDescription(record.get("description").asString(""));
                        out.add(node);
                    }
                    return out;
                });

        List<GraphEdge> entityEdges = neo4jAccessor.read(
                "MATCH (n:Note {userId: $userId})-[:CONTAINS]->(a:Entity), " +
                        "(n)-[:CONTAINS]->(b:Entity), (a)-[r:RELATED]->(b) " +
                        "WHERE true " + categoryFilter +
                        "RETURN a.name AS source, b.name AS target, r.relation AS relation LIMIT 500",
                params,
                (tx, cypher, p) -> {
                    List<GraphEdge> out = new ArrayList<>();
                    Result result = tx.run(cypher, p);
                    while (result.hasNext()) {
                        Record record = result.next();
                        GraphEdge edge = new GraphEdge();
                        edge.setSource(record.get("source").asString());
                        edge.setTarget(record.get("target").asString());
                        edge.setRelation(record.get("relation").asString());
                        out.add(edge);
                    }
                    return out;
                });

        List<GraphEdge> noteEdges = neo4jAccessor.read(
                "MATCH (n:Note {userId: $userId})-[:CONTAINS]->(e:Entity) " +
                        "WHERE true " + categoryFilter +
                        "RETURN n.id AS source, e.name AS target",
                params,
                (tx, cypher, p) -> {
                    List<GraphEdge> out = new ArrayList<>();
                    Result result = tx.run(cypher, p);
                    while (result.hasNext()) {
                        Record record = result.next();
                        GraphEdge edge = new GraphEdge();
                        edge.setSource(record.get("source").asString());
                        edge.setTarget(record.get("target").asString());
                        edge.setRelation("包含");
                        out.add(edge);
                    }
                    return out;
                });

        List<GraphNode> nodes = new ArrayList<>();
        if (noteNodes != null) {
            nodes.addAll(noteNodes);
        }
        if (entityNodes != null) {
            nodes.addAll(entityNodes);
        }
        List<GraphEdge> edges = new ArrayList<>();
        if (entityEdges != null) {
            edges.addAll(entityEdges);
        }
        if (noteEdges != null) {
            edges.addAll(noteEdges);
        }

        GraphData data = new GraphData();
        data.setNoteTitle(categoryId == null ? "全局知识体系" : "分类知识体系");
        data.setNodes(nodes);
        data.setEdges(edges);
        data.setExtractionSource("stored");
        return data;
    }

    public GraphData getGlobal() {
        return getByCategory(null);
    }

    public boolean delete(Long noteId) {
        Long userId = SecurityUtils.getUserId();
        Note note = noteMapper.selectById(noteId);
        // 归属校验方向与其他方法保持一致（Objects.equals 双侧 null 安全，
        // 避免历史脏数据 user_id 为 null 时 NPE→500）
        if (userId == null || note == null || !java.util.Objects.equals(note.getUserId(), userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "笔记不存在");
        }
        if (!neo4jAccessor.isAvailable()) {
            return false;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("noteId", "note-" + noteId);
        params.put("userId", String.valueOf(note.getUserId()));
        Boolean ok = neo4jAccessor.write(
                "MATCH (n:Note {id: $noteId}) DETACH DELETE n\n" +
                        "WITH count(*) AS ignored\n" +
                        "MATCH (e:Entity {userId: $userId}) WHERE size((e)--()) = 0 DELETE e",
                params,
                (tx, cypher, p) -> {
                    tx.run(cypher, p).consume();
                    return Boolean.TRUE;
                });
        return Boolean.TRUE.equals(ok);
    }

    public List<GraphNode> search(String keyword) {
        Long userId = SecurityUtils.getUserId();
        if (!StringUtils.hasText(keyword)) {
            return new ArrayList<>();
        }
        if (!neo4jAccessor.isAvailable()) {
            return new ArrayList<>();
        }
        Map<String, Object> params = new HashMap<>();
        params.put("kw", keyword.trim());
        params.put("userId", String.valueOf(userId));
        List<GraphNode> nodes = neo4jAccessor.read(
                "MATCH (n:Note {userId: $userId})-[:CONTAINS]->(e:Entity) " +
                        "WHERE e.name CONTAINS $kw " +
                        "RETURN e.name AS name, e.type AS type, e.description AS description, collect(DISTINCT n.title) AS notes " +
                        "ORDER BY size(e.name) LIMIT 50",
                params,
                (tx, cypher, p) -> {
                    List<GraphNode> out = new ArrayList<>();
                    Result result = tx.run(cypher, p);
                    while (result.hasNext()) {
                        Record record = result.next();
                        GraphNode node = new GraphNode();
                        node.setId(record.get("name").asString());
                        node.setName(record.get("name").asString());
                        node.setType(record.get("type").asString("其他"));
                        node.setDescription(record.get("description").asString(""));
                        out.add(node);
                    }
                    return out;
                });
        return nodes == null ? new ArrayList<>() : nodes;
    }

    public GraphOverview overview() {
        GraphOverview overview = new GraphOverview();
        if (!neo4jAccessor.isAvailable()) {
            overview.setAvailable(false);
            overview.setMessage("图谱服务（Neo4j）不可用");
            return overview;
        }
        Map<String, Object> empty = new HashMap<>();
        Long notes = neo4jAccessor.read("MATCH (n:Note) RETURN count(n) AS c", empty,
                (tx, cypher, p) -> tx.run(cypher, p).single().get("c").asLong());
        Long entities = neo4jAccessor.read("MATCH (e:Entity) RETURN count(e) AS c", empty,
                (tx, cypher, p) -> tx.run(cypher, p).single().get("c").asLong());
        Long relations = neo4jAccessor.read("MATCH (:Entity)-[r:RELATED]->() RETURN count(r) AS c", empty,
                (tx, cypher, p) -> tx.run(cypher, p).single().get("c").asLong());
        overview.setAvailable(true);
        overview.setNoteCount(notes == null ? 0 : notes);
        overview.setEntityCount(entities == null ? 0 : entities);
        overview.setRelationCount(relations == null ? 0 : relations);
        return overview;
    }

    private boolean persistGraph(Note note, List<GraphNode> entities, List<GraphEdge> relations) {
        Map<String, Object> params = new HashMap<>();
        params.put("noteId", "note-" + note.getId());
        params.put("title", note.getTitle() == null ? "" : note.getTitle());
        params.put("userId", String.valueOf(note.getUserId()));
        params.put("categoryId", note.getCategoryId() == null ? "" : String.valueOf(note.getCategoryId()));

        List<Map<String, Object>> entityParams = new ArrayList<>();
        for (GraphNode node : entities) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", node.getName());
            item.put("type", node.getType() == null ? "其他" : node.getType());
            item.put("description", node.getDescription() == null ? "" : node.getDescription());
            entityParams.add(item);
        }

        List<Map<String, Object>> relationParams = new ArrayList<>();
        for (GraphEdge edge : relations) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("source", edge.getSource());
            item.put("target", edge.getTarget());
            item.put("relation", edge.getRelation());
            relationParams.add(item);
        }

        Boolean ok = neo4jAccessor.write("", params, (tx, cypher, p) -> {
            tx.run("MATCH (n:Note {id: $noteId}) DETACH DELETE n", p).consume();
            tx.run("MATCH (e:Entity {userId: $userId}) WHERE size((e)--()) = 0 DELETE e", p).consume();
            tx.run("MERGE (n:Note {id: $noteId}) SET n.title = $title, n.userId = $userId, n.categoryId = $categoryId", p).consume();

            if (!entityParams.isEmpty()) {
                Map<String, Object> entityBatch = new HashMap<>(p);
                entityBatch.put("rows", entityParams);
                tx.run("UNWIND $rows AS row " +
                        "MERGE (e:Entity {name: row.name, userId: $userId}) " +
                        "SET e.type = row.type, e.description = row.description", entityBatch).consume();
                tx.run("UNWIND $rows AS row " +
                        "MATCH (n:Note {id: $noteId}) " +
                        "MATCH (e:Entity {name: row.name, userId: $userId}) " +
                        "MERGE (n)-[:CONTAINS]->(e)", entityBatch).consume();
            }
            if (!relationParams.isEmpty()) {
                Map<String, Object> relBatch = new HashMap<>(p);
                relBatch.put("rows", relationParams);
                tx.run("UNWIND $rows AS row " +
                        "MATCH (a:Entity {name: row.source, userId: $userId}), (b:Entity {name: row.target, userId: $userId}) " +
                        "MERGE (a)-[r:RELATED {relation: row.relation}]->(b)", relBatch).consume();
            }
            return Boolean.TRUE;
        });
        return Boolean.TRUE.equals(ok);
    }

    @SuppressWarnings("unchecked")
    private List<GraphNode> castEntities(Object value) {
        return value instanceof List ? (List<GraphNode>) value : new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private List<GraphEdge> castEdges(Object value) {
        return value instanceof List ? (List<GraphEdge>) value : new ArrayList<>();
    }
}
