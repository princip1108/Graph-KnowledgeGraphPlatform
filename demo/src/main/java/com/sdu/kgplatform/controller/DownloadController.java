package com.sdu.kgplatform.controller;

import com.sdu.kgplatform.dto.NodeDto;
import com.sdu.kgplatform.dto.RelationshipDto;
import com.sdu.kgplatform.entity.KnowledgeGraph;
import com.sdu.kgplatform.repository.KnowledgeGraphRepository;
import com.sdu.kgplatform.service.NodeService;
import com.sdu.kgplatform.service.RelationshipService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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

    private final KnowledgeGraphRepository graphRepository;
    private final NodeService nodeService;
    private final RelationshipService relationshipService;

    public DownloadController(KnowledgeGraphRepository graphRepository,
                              NodeService nodeService,
                              RelationshipService relationshipService) {
        this.graphRepository = graphRepository;
        this.nodeService = nodeService;
        this.relationshipService = relationshipService;
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
        
        KnowledgeGraph graph = graphOpt.get();
        String graphName = graph.getName() != null ? graph.getName() : "graph_" + graphId;
        // 清理文件名中的非法字符
        graphName = graphName.replaceAll("[\\\\/:*?\"<>|]", "_");
        
        try {
            // 获取节点和关系数据
            List<NodeDto> nodes = nodeService.getNodesByGraphId(graphId);
            List<RelationshipDto> relations = relationshipService.getRelationshipsByGraphId(graphId);
            
            switch (format.toLowerCase()) {
                case "json":
                    return downloadAsJson(graphName, graph, nodes, relations);
                case "csv":
                    return downloadAsCsv(graphName, nodes, relations);
                case "png":
                    return downloadAsPng(graphName, nodes, relations);
                default:
                    return ResponseEntity.badRequest().body(Map.of("error", "不支持的格式: " + format));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "下载失败: " + e.getMessage()));
        }
    }

    /**
     * 下载为 JSON 格式
     */
    private ResponseEntity<byte[]> downloadAsJson(String graphName, KnowledgeGraph graph,
                                                   List<NodeDto> nodes, List<RelationshipDto> relations) {
        // 构建 JSON 结构
        Map<String, Object> jsonData = new LinkedHashMap<>();
        jsonData.put("name", graph.getName());
        jsonData.put("description", graph.getDescription());
        
        // 节点数据
        List<Map<String, Object>> nodesList = new ArrayList<>();
        for (NodeDto node : nodes) {
            Map<String, Object> nodeMap = new LinkedHashMap<>();
            nodeMap.put("name", node.getName());
            nodeMap.put("type", node.getType());
            if (node.getDescription() != null) {
                nodeMap.put("description", node.getDescription());
            }
            nodesList.add(nodeMap);
        }
        jsonData.put("nodes", nodesList);
        
        // 关系数据 - 使用节点名称而非 ID
        Map<String, String> nodeIdToName = new HashMap<>();
        for (NodeDto node : nodes) {
            nodeIdToName.put(node.getNodeId(), node.getName());
        }
        
        List<Map<String, Object>> relationsList = new ArrayList<>();
        for (RelationshipDto rel : relations) {
            Map<String, Object> relMap = new LinkedHashMap<>();
            relMap.put("source", nodeIdToName.getOrDefault(rel.getSourceNodeId(), rel.getSourceNodeId()));
            relMap.put("target", nodeIdToName.getOrDefault(rel.getTargetNodeId(), rel.getTargetNodeId()));
            relMap.put("type", rel.getType());
            relationsList.add(relMap);
        }
        jsonData.put("relations", relationsList);
        
        // 转换为 JSON 字符串
        String jsonString = toJsonString(jsonData);
        byte[] content = jsonString.getBytes(StandardCharsets.UTF_8);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentDispositionFormData("attachment", graphName + ".json");
        
        return ResponseEntity.ok().headers(headers).body(content);
    }

    /**
     * 下载为 CSV 格式（包含节点表和关系表）
     */
    private ResponseEntity<byte[]> downloadAsCsv(String graphName,
                                                  List<NodeDto> nodes, List<RelationshipDto> relations) {
        StringBuilder csv = new StringBuilder();
        
        // 节点表
        csv.append("# 节点数据\n");
        csv.append("节点名称,节点类型,描述\n");
        for (NodeDto node : nodes) {
            csv.append(escapeCsv(node.getName())).append(",");
            csv.append(escapeCsv(node.getType())).append(",");
            csv.append(escapeCsv(node.getDescription() != null ? node.getDescription() : "")).append("\n");
        }
        
        csv.append("\n");
        
        // 关系表
        Map<String, String> nodeIdToName = new HashMap<>();
        for (NodeDto node : nodes) {
            nodeIdToName.put(node.getNodeId(), node.getName());
        }
        
        csv.append("# 关系数据\n");
        csv.append("源节点,目标节点,关系类型\n");
        for (RelationshipDto rel : relations) {
            csv.append(escapeCsv(nodeIdToName.getOrDefault(rel.getSourceNodeId(), rel.getSourceNodeId()))).append(",");
            csv.append(escapeCsv(nodeIdToName.getOrDefault(rel.getTargetNodeId(), rel.getTargetNodeId()))).append(",");
            csv.append(escapeCsv(rel.getType())).append("\n");
        }
        
        // 添加 BOM 以支持 Excel 正确识别 UTF-8
        byte[] bom = new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
        byte[] csvBytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        byte[] content = new byte[bom.length + csvBytes.length];
        System.arraycopy(bom, 0, content, 0, bom.length);
        System.arraycopy(csvBytes, 0, content, bom.length, csvBytes.length);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDispositionFormData("attachment", graphName + ".csv");
        
        return ResponseEntity.ok().headers(headers).body(content);
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

    /**
     * 简单的 JSON 序列化
     */
    private String toJsonString(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String) {
            return "\"" + escapeJson((String) obj) + "\"";
        }
        if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        }
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            StringBuilder sb = new StringBuilder("{\n");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(",\n");
                sb.append("  \"").append(escapeJson(entry.getKey().toString())).append("\": ");
                sb.append(toJsonString(entry.getValue()));
                first = false;
            }
            sb.append("\n}");
            return sb.toString();
        }
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            StringBuilder sb = new StringBuilder("[\n");
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(",\n");
                sb.append("    ").append(toJsonString(item));
                first = false;
            }
            sb.append("\n  ]");
            return sb.toString();
        }
        return "\"" + obj.toString() + "\"";
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
