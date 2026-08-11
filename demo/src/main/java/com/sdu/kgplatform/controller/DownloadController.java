package com.sdu.kgplatform.controller;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdu.kgplatform.dto.NodeDto;
import com.sdu.kgplatform.dto.RelationshipDto;
import com.sdu.kgplatform.entity.KnowledgeGraph;
import com.sdu.kgplatform.repository.KnowledgeGraphRepository;
import com.sdu.kgplatform.service.GraphService;
import com.sdu.kgplatform.service.NodeService;
import com.sdu.kgplatform.service.RelationshipService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

/**
 * 图谱下载控制器
 * 支持 JSON、CSV、PNG 三种格式
 */
@RestController
@RequestMapping("/api/download")
public class DownloadController {

    private static final Logger log = LoggerFactory.getLogger(DownloadController.class);
    private static final int EXPORT_BATCH_SIZE = 1000;
    private static final long PNG_MAX_NODES = 500;
    private static final long PNG_MAX_RELATIONS = 1500;
    private static final byte[] UTF8_BOM = new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };

    private final KnowledgeGraphRepository graphRepository;
    private final GraphService graphService;
    private final NodeService nodeService;
    private final RelationshipService relationshipService;
    private final ObjectMapper objectMapper;

    public DownloadController(KnowledgeGraphRepository graphRepository,
                              GraphService graphService,
                              NodeService nodeService,
                              RelationshipService relationshipService,
                              ObjectMapper objectMapper) {
        this.graphRepository = graphRepository;
        this.graphService = graphService;
        this.nodeService = nodeService;
        this.relationshipService = relationshipService;
        this.objectMapper = objectMapper;
    }

    /**
     * 下载图谱
     * GET /api/download/{graphId}?format=json|csv|png
     */
    @GetMapping("/{graphId}")
    public ResponseEntity<?> downloadGraph(
            @PathVariable Integer graphId,
            @RequestParam(value = "format", defaultValue = "json") String format) {

        // 获取图谱信息
        Optional<KnowledgeGraph> graphOpt = graphRepository.findById(graphId);
        if (graphOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!graphService.canViewGraph(graphId, com.sdu.kgplatform.common.SecurityUtils.getCurrentUserId())) {
            return ResponseEntity.status(403).body(Map.of("error", "无权下载该图谱"));
        }
        
        KnowledgeGraph graph = graphOpt.get();
        String graphName = sanitizeFileName(graph.getName() != null ? graph.getName() : "graph_" + graphId);
        String normalizedFormat = format == null ? "json" : format.toLowerCase(Locale.ROOT);

        try {
            switch (normalizedFormat) {
                case "json":
                    graphService.incrementDownloadCount(graphId);
                    return downloadAsJson(graphId, graphName, graph);
                case "csv":
                    graphService.incrementDownloadCount(graphId);
                    return downloadAsCsv(graphId, graphName);
                case "png":
                    long nodeCount = nodeService.countNodesByGraphId(graphId);
                    long relationCount = relationshipService.countRelationshipsByGraphId(graphId);
                    if (nodeCount > PNG_MAX_NODES || relationCount > PNG_MAX_RELATIONS) {
                        return ResponseEntity.badRequest().body(Map.of(
                                "error", "图谱过大，PNG 导出已限制以避免内存溢出",
                                "nodeCount", nodeCount,
                                "relationCount", relationCount,
                                "maxNodes", PNG_MAX_NODES,
                                "maxRelations", PNG_MAX_RELATIONS,
                                "suggestion", "请使用 json 或 csv 格式下载完整数据"));
                    }
                    List<NodeDto> nodes = nodeService.getNodesByGraphId(graphId);
                    List<RelationshipDto> relations = relationshipService.getRelationshipsByGraphId(graphId);
                    graphService.incrementDownloadCount(graphId);
                    return downloadAsPng(graphName, nodes, relations);
                default:
                    return ResponseEntity.badRequest().body(Map.of("error", "不支持的格式: " + format));
            }
        } catch (Exception e) {
            log.error("Download graph failed, graphId={}, format={}", graphId, format, e);
            return ResponseEntity.internalServerError().body(Map.of("error", "下载失败: " + e.getMessage()));
        }
    }

    /**
     * 下载为 JSON 格式
     */
    private ResponseEntity<StreamingResponseBody> downloadAsJson(Integer graphId, String graphName, KnowledgeGraph graph) {
        StreamingResponseBody body = outputStream -> {
            try (JsonGenerator json = objectMapper.getFactory().createGenerator(outputStream)) {
                json.useDefaultPrettyPrinter();
                json.writeStartObject();
                json.writeStringField("name", graph.getName());
                json.writeStringField("description", graph.getDescription());

                json.writeArrayFieldStart("nodes");
                streamNodesJson(graphId, json);
                json.writeEndArray();

                json.writeArrayFieldStart("relations");
                streamRelationsJson(graphId, json);
                json.writeEndArray();

                json.writeEndObject();
            } catch (Exception e) {
                log.error("Stream JSON export failed, graphId={}", graphId, e);
                throw asIOException("JSON export failed", e);
            }
        };

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentDispositionFormData("attachment", graphName + ".json");

        return ResponseEntity.ok().headers(headers).body(body);
    }

    private void streamNodesJson(Integer graphId, JsonGenerator json) throws IOException {
        for (long skip = 0; ; skip += EXPORT_BATCH_SIZE) {
            List<NodeDto> nodes = nodeService.getExportNodesByGraphId(graphId, skip, EXPORT_BATCH_SIZE);
            if (nodes.isEmpty()) {
                return;
            }
            for (NodeDto node : nodes) {
                json.writeStartObject();
                json.writeStringField("name", node.getName());
                json.writeStringField("type", node.getType());
                if (node.getDescription() != null) {
                    json.writeStringField("description", node.getDescription());
                }
                json.writeEndObject();
            }
            json.flush();
            if (nodes.size() < EXPORT_BATCH_SIZE) {
                return;
            }
        }
    }

    private void streamRelationsJson(Integer graphId, JsonGenerator json) throws IOException {
        for (long skip = 0; ; skip += EXPORT_BATCH_SIZE) {
            List<RelationshipDto> relations = relationshipService.getExportRelationshipsByGraphId(graphId, skip, EXPORT_BATCH_SIZE);
            if (relations.isEmpty()) {
                return;
            }
            for (RelationshipDto rel : relations) {
                json.writeStartObject();
                json.writeStringField("source", valueOrFallback(rel.getSourceNodeName(), rel.getSourceNodeId()));
                json.writeStringField("target", valueOrFallback(rel.getTargetNodeName(), rel.getTargetNodeId()));
                json.writeStringField("type", rel.getType());
                json.writeEndObject();
            }
            json.flush();
            if (relations.size() < EXPORT_BATCH_SIZE) {
                return;
            }
        }
    }

    /**
     * 下载为 CSV 格式（包含节点表和关系表）
     */
    private ResponseEntity<StreamingResponseBody> downloadAsCsv(Integer graphId, String graphName) {
        StreamingResponseBody body = outputStream -> {
            try {
                outputStream.write(UTF8_BOM);
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
                streamNodesCsv(graphId, writer);
                writer.write("\n");
                streamRelationsCsv(graphId, writer);
                writer.flush();
            } catch (Exception e) {
                log.error("Stream CSV export failed, graphId={}", graphId, e);
                throw asIOException("CSV export failed", e);
            }
        };

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDispositionFormData("attachment", graphName + ".csv");

        return ResponseEntity.ok().headers(headers).body(body);
    }

    private void streamNodesCsv(Integer graphId, BufferedWriter writer) throws IOException {
        writer.write("# 节点数据\n");
        writer.write("节点名称,节点类型,描述\n");
        for (long skip = 0; ; skip += EXPORT_BATCH_SIZE) {
            List<NodeDto> nodes = nodeService.getExportNodesByGraphId(graphId, skip, EXPORT_BATCH_SIZE);
            if (nodes.isEmpty()) {
                return;
            }
            for (NodeDto node : nodes) {
                writer.write(escapeCsv(node.getName()));
                writer.write(",");
                writer.write(escapeCsv(node.getType()));
                writer.write(",");
                writer.write(escapeCsv(node.getDescription()));
                writer.write("\n");
            }
            writer.flush();
            if (nodes.size() < EXPORT_BATCH_SIZE) {
                return;
            }
        }
    }

    private void streamRelationsCsv(Integer graphId, BufferedWriter writer) throws IOException {
        writer.write("# 关系数据\n");
        writer.write("源节点,目标节点,关系类型\n");
        for (long skip = 0; ; skip += EXPORT_BATCH_SIZE) {
            List<RelationshipDto> relations = relationshipService.getExportRelationshipsByGraphId(graphId, skip, EXPORT_BATCH_SIZE);
            if (relations.isEmpty()) {
                return;
            }
            for (RelationshipDto rel : relations) {
                writer.write(escapeCsv(valueOrFallback(rel.getSourceNodeName(), rel.getSourceNodeId())));
                writer.write(",");
                writer.write(escapeCsv(valueOrFallback(rel.getTargetNodeName(), rel.getTargetNodeId())));
                writer.write(",");
                writer.write(escapeCsv(rel.getType()));
                writer.write("\n");
            }
            writer.flush();
            if (relations.size() < EXPORT_BATCH_SIZE) {
                return;
            }
        }
    }

    /**
     * 下载为 PNG 图片
     */
    private ResponseEntity<byte[]> downloadAsPng(String graphName,
                                                  List<NodeDto> nodes, List<RelationshipDto> relations) throws Exception {
        int width = 800;
        int height = 600;
        
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        
        // 抗锯齿
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        
        // 背景色
        g2d.setColor(new Color(248, 250, 252));
        g2d.fillRect(0, 0, width, height);
        
        if (nodes.isEmpty()) {
            g2d.setColor(Color.GRAY);
            g2d.setFont(new Font("SansSerif", Font.PLAIN, 16));
            g2d.drawString("暂无图谱数据", width / 2 - 50, height / 2);
        } else {
            // 计算节点位置（环形布局）
            Map<String, int[]> nodePositions = new HashMap<>();
            int centerX = width / 2;
            int centerY = height / 2;
            int radius = Math.min(width, height) / 2 - 80;
            
            for (int i = 0; i < nodes.size(); i++) {
                double angle = (2 * Math.PI * i) / nodes.size();
                int x = (int) (centerX + radius * Math.cos(angle));
                int y = (int) (centerY + radius * Math.sin(angle));
                nodePositions.put(nodes.get(i).getNodeId(), new int[]{x, y});
            }
            
            // 绘制边
            g2d.setColor(new Color(203, 213, 225));
            g2d.setStroke(new BasicStroke(1.5f));
            for (RelationshipDto rel : relations) {
                int[] src = nodePositions.get(rel.getSourceNodeId());
                int[] tgt = nodePositions.get(rel.getTargetNodeId());
                if (src != null && tgt != null) {
                    g2d.drawLine(src[0], src[1], tgt[0], tgt[1]);
                }
            }
            
            // 绘制节点
            int nodeRadius = 20;
            g2d.setFont(new Font("SansSerif", Font.PLAIN, 10));
            for (NodeDto node : nodes) {
                int[] pos = nodePositions.get(node.getNodeId());
                if (pos != null) {
                    // 节点颜色
                    Color nodeColor = getNodeColor(node.getType());
                    g2d.setColor(nodeColor);
                    g2d.fillOval(pos[0] - nodeRadius, pos[1] - nodeRadius, nodeRadius * 2, nodeRadius * 2);
                    
                    // 节点边框
                    g2d.setColor(nodeColor.darker());
                    g2d.drawOval(pos[0] - nodeRadius, pos[1] - nodeRadius, nodeRadius * 2, nodeRadius * 2);
                    
                    // 节点标签
                    g2d.setColor(Color.DARK_GRAY);
                    String label = node.getName();
                    if (label.length() > 6) {
                        label = label.substring(0, 6) + "...";
                    }
                    FontMetrics fm = g2d.getFontMetrics();
                    int labelWidth = fm.stringWidth(label);
                    g2d.drawString(label, pos[0] - labelWidth / 2, pos[1] + nodeRadius + 15);
                }
            }
        }
        
        g2d.dispose();
        
        // 转换为 PNG 字节数组
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        byte[] content = baos.toByteArray();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        headers.setContentDispositionFormData("attachment", graphName + ".png");
        
        return ResponseEntity.ok().headers(headers).body(content);
    }

    /**
     * 获取节点颜色
     */
    private Color getNodeColor(String type) {
        if (type == null) return new Color(107, 114, 128);
        switch (type) {
            case "人物": return new Color(59, 130, 246);
            case "组织": return new Color(16, 185, 129);
            case "地点": return new Color(245, 158, 11);
            case "事件": return new Color(239, 68, 68);
            case "概念": return new Color(139, 92, 246);
            case "作品": return new Color(236, 72, 153);
            case "时间": return new Color(6, 182, 212);
            default: return new Color(107, 114, 128);
        }
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String valueOrFallback(String value, String fallback) {
        return value != null ? value : fallback;
    }

    private IOException asIOException(String message, Exception e) {
        return e instanceof IOException ioException ? ioException : new IOException(message, e);
    }

    /**
     * CSV 转义
     */
    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

}
