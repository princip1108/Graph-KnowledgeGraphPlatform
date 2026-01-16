package com.sdu.kgplatform.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdu.kgplatform.dto.*;
import com.sdu.kgplatform.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GraphImportServiceImpl implements GraphImportService {

    private final GraphService graphService;
    private final NodeService nodeService;
    private final RelationshipService relationshipService;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;

    @Override
    public GraphDetailDto importGraph(MultipartFile file, String name, String description, String status,
            MultipartFile coverFile, Integer userId) {
        // 1. 验证文件
        if (file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的图谱文件");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.toLowerCase().endsWith(".json"))) {
            throw new IllegalArgumentException("只支持 JSON 格式的图谱文件");
        }

        try {
            // 2. 解析 JSON
            GraphImportDto importData = objectMapper.readValue(file.getInputStream(), GraphImportDto.class);

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
                coverUrl = fileStorageService.storeFile(coverFile, "covers");
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

            GraphDetailDto createdGraph = graphService.createGraph(userId, createDto);
            Integer graphId = createdGraph.getGraphId();

            // 6. 创建节点
            Map<String, String> nodeNameToId = new HashMap<>();
            int nodeCount = 0;
            if (importData.getNodes() != null) {
                for (GraphImportDto.NodeImportItem item : importData.getNodes()) {
                    if (item.getName() != null && !item.getName().isEmpty()) {
                        NodeDto nodeDto = new NodeDto();
                        nodeDto.setName(item.getName());
                        nodeDto.setType(item.getType() != null ? item.getType() : "默认");
                        nodeDto.setDescription(item.getDescription());

                        try {
                            NodeDto created = nodeService.createNode(graphId, nodeDto);
                            nodeNameToId.put(item.getName(), created.getNodeId());
                            nodeCount++;
                        } catch (Exception e) {
                            log.warn("Failed to create node: {}", item.getName(), e);
                        }
                    }
                }
            }

            // 7. 创建关系
            int relationCount = 0;
            if (importData.getRelations() != null) {
                for (GraphImportDto.RelationImportItem item : importData.getRelations()) {
                    String sourceName = item.getSource();
                    String targetName = item.getTarget();

                    if (sourceName != null && targetName != null) {
                        String sourceId = nodeNameToId.get(sourceName);
                        String targetId = nodeNameToId.get(targetName);

                        if (sourceId != null && targetId != null) {
                            RelationshipDto relDto = new RelationshipDto();
                            relDto.setSourceNodeId(sourceId);
                            relDto.setTargetNodeId(targetId);
                            relDto.setType(item.getType() != null ? item.getType() : "关联");

                            try {
                                relationshipService.createRelationship(graphId, relDto);
                                relationCount++;
                            } catch (Exception e) {
                                log.warn("Failed to create relationship: {} -> {}", sourceName, targetName, e);
                            }
                        }
                    }
                }
            }

            // 8. 更新统计信息
            graphService.updateGraphStats(graphId, nodeCount, relationCount);

            // 返回更新后的详情 (此处简单返回创建时的对象，实际可能需要reload，但暂时够用)
            createdGraph.setNodeCount(nodeCount);
            createdGraph.setRelationCount(relationCount);
            return createdGraph;

        } catch (IOException e) {
            throw new RuntimeException("JSON parsing failed: " + e.getMessage(), e);
        }
    }
}
