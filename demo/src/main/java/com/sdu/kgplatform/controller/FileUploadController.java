package com.sdu.kgplatform.controller;

import com.sdu.kgplatform.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.sdu.kgplatform.dto.GraphCreateDto;
import com.sdu.kgplatform.dto.GraphDetailDto;
import com.sdu.kgplatform.dto.NodeDto;
import com.sdu.kgplatform.dto.RelationshipDto;
import com.sdu.kgplatform.entity.User;
import com.sdu.kgplatform.repository.UserRepository;
import com.sdu.kgplatform.service.GraphService;
import com.sdu.kgplatform.service.NodeService;
import com.sdu.kgplatform.service.RelationshipService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * 文件上传控制器
 */
@RestController
@RequestMapping("/api/upload")
public class FileUploadController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final GraphService graphService;
    private final NodeService nodeService;
    private final RelationshipService relationshipService;
    
    // 上传文件根目录（从配置文件读取，默认为相对路径）
    @Value("${app.upload.base-path:uploads}")
    private String uploadBasePath;

    public FileUploadController(UserService userService,
                                UserRepository userRepository,
                                GraphService graphService,
                                NodeService nodeService,
                                RelationshipService relationshipService) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.graphService = graphService;
        this.nodeService = nodeService;
        this.relationshipService = relationshipService;
    }
    
    // 获取头像存储目录
    private String getAvatarDir() {
        return uploadBasePath + "/avatars";
    }
    
    // 获取封面存储目录
    private String getCoverDir() {
        return uploadBasePath + "/covers";
    }

    /**
     * 上传头像
     */
    @PostMapping("/avatar")
    public ResponseEntity<?> uploadAvatar(@RequestParam("file") MultipartFile file) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        // 验证文件
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请选择要上传的文件"));
        }

        // 验证文件类型
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("error", "只能上传图片文件"));
        }

        // 验证文件大小（最大2MB）
        if (file.getSize() > 2 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("error", "图片大小不能超过2MB"));
        }

        try {
            // 创建存储目录
            Path uploadPath = Paths.get(getAvatarDir());
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // 生成唯一文件名
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String newFilename = UUID.randomUUID().toString() + extension;

            // 保存文件
            Path filePath = uploadPath.resolve(newFilename);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // 生成访问URL
            String avatarUrl = "/uploads/avatars/" + newFilename;

            // 更新用户头像
            userService.updateUserAvatar(auth.getName(), avatarUrl);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "avatarUrl", avatarUrl,
                "message", "头像上传成功"
            ));

        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of("error", "文件上传失败: " + e.getMessage()));
        }
    }

    /**
     * 上传帖子图片（用于 Markdown 编辑器）
     */
    @PostMapping("/image")
    public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请选择要上传的文件"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("error", "只能上传图片文件"));
        }

        if (file.getSize() > 5 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("error", "图片大小不能超过5MB"));
        }

        try {
            Path uploadPath = Paths.get(uploadBasePath + "/images");
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String newFilename = UUID.randomUUID().toString() + extension;

            Path filePath = uploadPath.resolve(newFilename);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            String imageUrl = "/uploads/images/" + newFilename;

            return ResponseEntity.ok(Map.of(
                "success", true,
                "url", imageUrl,
                "message", "图片上传成功"
            ));

        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of("error", "文件上传失败: " + e.getMessage()));
        }
    }

    /**
     * 上传图谱封面
     */
    @PostMapping("/cover")
    public ResponseEntity<?> uploadCover(@RequestParam("file") MultipartFile file) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        // 验证文件
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请选择要上传的文件"));
        }

        // 验证文件类型
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("error", "只能上传图片文件"));
        }

        // 验证文件大小（最大5MB）
        if (file.getSize() > 5 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("error", "图片大小不能超过5MB"));
        }

        try {
            // 创建存储目录
            Path uploadPath = Paths.get(getCoverDir());
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // 生成唯一文件名
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String newFilename = UUID.randomUUID().toString() + extension;

            // 保存文件
            Path filePath = uploadPath.resolve(newFilename);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // 生成访问URL
            String coverUrl = "/uploads/covers/" + newFilename;

            return ResponseEntity.ok(Map.of(
                "success", true,
                "url", coverUrl,
                "message", "封面上传成功"
            ));

        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of("error", "文件上传失败: " + e.getMessage()));
        }
    }

    /**
     * 上传图谱文件（JSON格式）
     * 支持的格式：
     * {
     *   "name": "图谱名称",
     *   "description": "图谱描述",
     *   "nodes": [
     *     {"name": "节点1", "type": "类型1"},
     *     {"name": "节点2", "type": "类型2"}
     *   ],
     *   "relations": [
     *     {"source": "节点1", "target": "节点2", "type": "关系类型"}
     *   ]
     * }
     */
    @PostMapping("/graph")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> uploadGraph(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "status", defaultValue = "DRAFT") String status,
            @RequestParam(value = "cover", required = false) MultipartFile coverFile) {
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        // 验证文件
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请选择要上传的图谱文件"));
        }

        // 验证文件类型
        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".json") && !filename.endsWith(".JSON"))) {
            return ResponseEntity.badRequest().body(Map.of("error", "只支持 JSON 格式的图谱文件"));
        }

        try {
            // 获取当前用户ID
            String account = auth.getName();
            User user = null;
            
            // 根据账号类型查找用户：邮箱、手机号或用户名
            if (account.contains("@")) {
                user = userRepository.findByEmail(account).orElse(null);
            }
            if (user == null) {
                user = userRepository.findByPhone(account).orElse(null);
            }
            if (user == null) {
                user = userRepository.findByUserName(account).orElse(null);
            }
            if (user == null) {
                throw new IllegalArgumentException("用户不存在: " + account);
            }
            Integer userId = user.getUserId();

            // 读取文件内容并解析JSON
            String jsonContent = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
            Map<String, Object> jsonData = parseJson(jsonContent);

            // 获取图谱名称（优先使用参数，其次使用文件中的name字段）
            String graphName = name;
            if (graphName == null || graphName.isEmpty()) {
                graphName = (String) jsonData.get("name");
            }
            if (graphName == null || graphName.isEmpty()) {
                // 使用文件名作为图谱名称
                graphName = filename.replace(".json", "").replace(".JSON", "");
            }

            // 获取描述
            String graphDesc = description;
            if (graphDesc == null || graphDesc.isEmpty()) {
                graphDesc = (String) jsonData.getOrDefault("description", "");
            }

            // 上传封面图片（如果有）
            String coverUrl = null;
            if (coverFile != null && !coverFile.isEmpty()) {
                Path uploadPath = Paths.get(getCoverDir());
                if (!Files.exists(uploadPath)) {
                    Files.createDirectories(uploadPath);
                }
                String ext = "";
                String origName = coverFile.getOriginalFilename();
                if (origName != null && origName.contains(".")) {
                    ext = origName.substring(origName.lastIndexOf("."));
                }
                String newFilename = UUID.randomUUID().toString() + ext;
                Path filePath = uploadPath.resolve(newFilename);
                Files.copy(coverFile.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
                coverUrl = "/uploads/covers/" + newFilename;
            }

            // 创建图谱
            GraphCreateDto createDto = new GraphCreateDto();
            createDto.setName(graphName);
            createDto.setDescription(graphDesc);
            createDto.setStatus(status);
            createDto.setCoverImage(coverUrl);

            GraphDetailDto createdGraph = graphService.createGraph(userId, createDto);
            Integer graphId = createdGraph.getGraphId();

            // 解析并创建节点
            Map<String, String> nodeNameToId = new HashMap<>();
            int nodeCount = 0;
            int relationCount = 0;

            List<Map<String, Object>> nodes = (List<Map<String, Object>>) jsonData.get("nodes");
            if (nodes != null) {
                for (Map<String, Object> nodeJson : nodes) {
                    String nodeName = (String) nodeJson.get("name");
                    String nodeType = (String) nodeJson.getOrDefault("type", "默认");
                    
                    if (nodeName != null && !nodeName.isEmpty()) {
                        NodeDto nodeDto = new NodeDto();
                        nodeDto.setName(nodeName);
                        nodeDto.setType(nodeType);
                        
                        try {
                            NodeDto created = nodeService.createNode(graphId, nodeDto);
                            nodeNameToId.put(nodeName, created.getNodeId());
                            nodeCount++;
                        } catch (Exception e) {
                            // 忽略单个节点创建失败
                        }
                    }
                }
            }

            // 解析并创建关系
            List<Map<String, Object>> relations = (List<Map<String, Object>>) jsonData.get("relations");
            System.out.println("Parsing relations from JSON: " + (relations != null ? relations.size() : 0) + " relations found");
            if (relations != null) {
                for (Map<String, Object> relJson : relations) {
                    String sourceName = (String) relJson.get("source");
                    String targetName = (String) relJson.get("target");
                    String relType = (String) relJson.getOrDefault("type", "关联");
                    
                    System.out.println("Processing relation: " + sourceName + " -> " + targetName + " [" + relType + "]");

                    if (sourceName != null && targetName != null) {
                        String sourceId = nodeNameToId.get(sourceName);
                        String targetId = nodeNameToId.get(targetName);
                        
                        System.out.println("  Source ID: " + sourceId + ", Target ID: " + targetId);

                        if (sourceId != null && targetId != null) {
                            RelationshipDto relDto = new RelationshipDto();
                            relDto.setSourceNodeId(sourceId);
                            relDto.setTargetNodeId(targetId);
                            relDto.setType(relType);

                            try {
                                relationshipService.createRelationship(graphId, relDto);
                                relationCount++;
                                System.out.println("  Relation created successfully");
                            } catch (Exception e) {
                                System.out.println("  Failed to create relation: " + e.getMessage());
                                e.printStackTrace();
                            }
                        } else {
                            System.out.println("  Skipped: source or target node not found");
                        }
                    }
                }
            }
            System.out.println("Total relations created: " + relationCount);

            // 更新图谱的节点数和关系数
            graphService.updateGraphStats(graphId, nodeCount, relationCount);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "图谱上传成功",
                "graphId", graphId,
                "graphName", graphName,
                "nodeCount", nodeCount,
                "relationCount", relationCount
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                "error", "图谱上传失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 简单的 JSON 解析方法
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        Map<String, Object> result = new LinkedHashMap<>();
        
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);
        
        // 解析 name
        java.util.regex.Pattern namePattern = java.util.regex.Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
        java.util.regex.Matcher nameMatcher = namePattern.matcher(json);
        if (nameMatcher.find()) {
            result.put("name", nameMatcher.group(1));
        }
        
        // 解析 description
        java.util.regex.Pattern descPattern = java.util.regex.Pattern.compile("\"description\"\\s*:\\s*\"([^\"]+)\"");
        java.util.regex.Matcher descMatcher = descPattern.matcher(json);
        if (descMatcher.find()) {
            result.put("description", descMatcher.group(1));
        }
        
        // 解析 nodes 数组
        List<Map<String, Object>> nodes = new ArrayList<>();
        java.util.regex.Pattern nodesPattern = java.util.regex.Pattern.compile("\"nodes\"\\s*:\\s*\\[(.*?)\\]", java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher nodesMatcher = nodesPattern.matcher(json);
        if (nodesMatcher.find()) {
            String nodesStr = nodesMatcher.group(1);
            java.util.regex.Pattern nodePattern = java.util.regex.Pattern.compile("\\{([^}]+)\\}");
            java.util.regex.Matcher nodeMatcher = nodePattern.matcher(nodesStr);
            while (nodeMatcher.find()) {
                Map<String, Object> node = new LinkedHashMap<>();
                String nodeStr = nodeMatcher.group(1);
                java.util.regex.Pattern propPattern = java.util.regex.Pattern.compile("\"(\\w+)\"\\s*:\\s*(?:\"([^\"]*)\"|([\\d]+))");
                java.util.regex.Matcher propMatcher = propPattern.matcher(nodeStr);
                while (propMatcher.find()) {
                    String key = propMatcher.group(1);
                    String strVal = propMatcher.group(2);
                    String numVal = propMatcher.group(3);
                    if (strVal != null) {
                        node.put(key, strVal);
                    } else if (numVal != null) {
                        node.put(key, Integer.parseInt(numVal));
                    }
                }
                if (!node.isEmpty()) {
                    nodes.add(node);
                }
            }
        }
        result.put("nodes", nodes);
        
        // 解析 relations 数组
        List<Map<String, Object>> relations = new ArrayList<>();
        java.util.regex.Pattern relPattern = java.util.regex.Pattern.compile("\"relations\"\\s*:\\s*\\[(.*?)\\]", java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher relMatcher = relPattern.matcher(json);
        if (relMatcher.find()) {
            String relsStr = relMatcher.group(1);
            java.util.regex.Pattern relItemPattern = java.util.regex.Pattern.compile("\\{([^}]+)\\}");
            java.util.regex.Matcher relItemMatcher = relItemPattern.matcher(relsStr);
            while (relItemMatcher.find()) {
                Map<String, Object> rel = new LinkedHashMap<>();
                String relStr = relItemMatcher.group(1);
                java.util.regex.Pattern propPattern = java.util.regex.Pattern.compile("\"(\\w+)\"\\s*:\\s*\"([^\"]+)\"");
                java.util.regex.Matcher propMatcher = propPattern.matcher(relStr);
                while (propMatcher.find()) {
                    rel.put(propMatcher.group(1), propMatcher.group(2));
                }
                if (!rel.isEmpty()) {
                    relations.add(rel);
                }
            }
        }
        result.put("relations", relations);
        return result;
    }
}
