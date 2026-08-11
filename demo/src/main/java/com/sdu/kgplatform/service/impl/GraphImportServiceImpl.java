package com.sdu.kgplatform.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdu.kgplatform.dto.*;
import com.sdu.kgplatform.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GraphImportServiceImpl implements GraphImportService {

    private final GraphService graphService;
    private final GraphImportBatchWriter graphImportBatchWriter;
    private final FileStorageService fileStorageService;
    private final FileValidationService fileValidationService;
    private final ObjectMapper objectMapper;

    @Override
    public GraphDetailDto importGraph(MultipartFile file, String name, String description, String status,
            String domain, MultipartFile coverFile, Integer userId) {
        // 1. 验证文件
        if (file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的图谱文件");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.toLowerCase().endsWith(".json"))) {
            throw new IllegalArgumentException("只支持 JSON 格式的图谱文件");
        }

        GraphDetailDto createdGraph = null;
        String storedCoverUrl = null;

        try {
            // 2. 解析 JSON
            GraphImportDto importData = objectMapper.readValue(file.getInputStream(), GraphImportDto.class);
            validateImportData(importData);

            // 3. 确定图谱基本信息
            String graphName = name;
            if (graphName == null || graphName.isEmpty()) {
                graphName = importData.getName();
            }
            if (graphName == null || graphName.isEmpty()) {
                graphName = filename.replace(".json", "").replace(".JSON", "");
            }

            String graphDesc = description;
            if (graphDesc == null || graphDesc.isEmpty()) {
                graphDesc = importData.getDescription();
            }

            // 4. 处理封面
            String coverUrl = null;
            boolean isCustomCover = false;
            if (coverFile != null && !coverFile.isEmpty()) {
                fileValidationService.validateImage(coverFile, FileValidationService.IMAGE_MAX_SIZE);
                coverUrl = fileStorageService.storeFile(coverFile, "covers");
                storedCoverUrl = coverUrl;
                isCustomCover = true; // 用户上传的封面
            } else {
                coverUrl = importData.getCoverImage();
                isCustomCover = (coverUrl != null && !coverUrl.isEmpty()); // JSON中指定的封面也视为自定义
            }

            // 5. 创建图谱实体
            GraphCreateDto createDto = new GraphCreateDto();
            createDto.setName(graphName);
            createDto.setDescription(graphDesc);
            createDto.setStatus(status);
            createDto.setCoverImage(coverUrl);
            createDto.setIsCustomCover(isCustomCover);
            createDto.setDomain(domain != null ? domain : "other");

            createdGraph = graphService.createGraph(userId, createDto);
            Integer graphId = createdGraph.getGraphId();

            // 6. 创建节点
            Map<String, String> nodeNameToId = new HashMap<>();
            List<GraphImportBatchWriter.ImportedNode> nodes = new ArrayList<>(importData.getNodes().size());
            for (GraphImportDto.NodeImportItem item : importData.getNodes()) {
                String nodeName = item.getName().trim();
                String nodeId = UUID.randomUUID().toString();
                nodeNameToId.put(nodeName, nodeId);
                nodes.add(new GraphImportBatchWriter.ImportedNode(
                        nodeId,
                        nodeName,
                        item.getType() != null ? item.getType() : "默认",
                        item.getDescription()));
            }

            // 7. 创建关系
            List<GraphImportBatchWriter.ImportedRelationship> relationships = new ArrayList<>();
            if (importData.getRelations() != null) {
                relationships = new ArrayList<>(importData.getRelations().size());
                for (GraphImportDto.RelationImportItem item : importData.getRelations()) {
                    String sourceId = nodeNameToId.get(item.getSource().trim());
                    String targetId = nodeNameToId.get(item.getTarget().trim());

                    relationships.add(new GraphImportBatchWriter.ImportedRelationship(
                            sourceId,
                            targetId,
                            item.getType() != null ? item.getType() : "关联"));
                }
            }

            GraphImportBatchWriter.BatchWriteResult writeResult =
                    graphImportBatchWriter.writeGraph(graphId, nodes, relationships);

            // 8. 更新统计信息
            graphService.updateGraphStats(graphId, writeResult.nodeCount(), writeResult.relationCount());

            // 返回更新后的详情 (此处简单返回创建时的对象，实际可能需要reload，但暂时够用)
            createdGraph.setNodeCount(writeResult.nodeCount());
            createdGraph.setRelationCount(writeResult.relationCount());
            return createdGraph;

        } catch (IOException e) {
            rollbackCreatedData(createdGraph, storedCoverUrl);
            throw new RuntimeException("JSON parsing failed: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            rollbackCreatedData(createdGraph, storedCoverUrl);
            throw e;
        }
    }

    private void validateImportData(GraphImportDto importData) {
        if (importData == null) {
            throw new IllegalArgumentException("图谱文件内容为空或格式错误");
        }
        if (importData.getNodes() == null || importData.getNodes().isEmpty()) {
            throw new IllegalArgumentException("图谱文件至少需要包含一个节点");
        }

        Set<String> nodeNames = new HashSet<>();
        for (int i = 0; i < importData.getNodes().size(); i++) {
            GraphImportDto.NodeImportItem node = importData.getNodes().get(i);
            String nodeName = node.getName() == null ? null : node.getName().trim();
            if (nodeName == null || nodeName.isEmpty()) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 个节点名称不能为空");
            }
            if (!nodeNames.add(nodeName)) {
                throw new IllegalArgumentException("图谱文件存在重复节点名称: " + nodeName);
            }
        }

        if (importData.getRelations() == null) {
            return;
        }
        for (int i = 0; i < importData.getRelations().size(); i++) {
            GraphImportDto.RelationImportItem relation = importData.getRelations().get(i);
            String source = relation.getSource() == null ? null : relation.getSource().trim();
            String target = relation.getTarget() == null ? null : relation.getTarget().trim();
            if (source == null || source.isEmpty() || target == null || target.isEmpty()) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 条关系的源节点和目标节点不能为空");
            }
            if (!nodeNames.contains(source)) {
                throw new IllegalArgumentException("关系引用了不存在的源节点: " + source);
            }
            if (!nodeNames.contains(target)) {
                throw new IllegalArgumentException("关系引用了不存在的目标节点: " + target);
            }
        }
    }

    private void rollbackCreatedData(GraphDetailDto createdGraph, String storedCoverUrl) {
        if (createdGraph != null && createdGraph.getGraphId() != null) {
            try {
                graphService.adminDeleteGraphBestEffort(createdGraph.getGraphId());
            } catch (Exception rollbackError) {
                log.warn("Failed to rollback imported graph: {}", createdGraph.getGraphId(), rollbackError);
            }
            return;
        }

        if (storedCoverUrl != null) {
            fileStorageService.deleteFile(storedCoverUrl);
        }
    }
}
