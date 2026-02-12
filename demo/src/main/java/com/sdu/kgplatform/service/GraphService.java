package com.sdu.kgplatform.service;

import com.sdu.kgplatform.dto.GraphCreateDto;
import com.sdu.kgplatform.dto.GraphDetailDto;
import com.sdu.kgplatform.dto.GraphListDto;
import com.sdu.kgplatform.dto.GraphUpdateDto;
import com.sdu.kgplatform.entity.CachedFileFormat;
import com.sdu.kgplatform.entity.GraphStatus;
import com.sdu.kgplatform.entity.KnowledgeGraph;
import com.sdu.kgplatform.entity.User;
import com.sdu.kgplatform.repository.KnowledgeGraphRepository;
import com.sdu.kgplatform.repository.NodeRepository;
import com.sdu.kgplatform.repository.RelationshipRepository;
import com.sdu.kgplatform.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 知识图谱服务层
 */
@Service
public class GraphService {

    private final KnowledgeGraphRepository graphRepository;
    private final UserRepository userRepository;
    private final NodeRepository nodeRepository;
    private final com.sdu.kgplatform.repository.CategoryRepository categoryRepository;
    private final FileStorageService fileStorageService;
    private final com.sdu.kgplatform.repository.BrowsingHistoryRepository browsingHistoryRepository;

    private final RelationshipRepository relationshipRepository;

    public GraphService(KnowledgeGraphRepository graphRepository,
            UserRepository userRepository,
            NodeRepository nodeRepository,
            RelationshipRepository relationshipRepository,
            com.sdu.kgplatform.repository.CategoryRepository categoryRepository,
            FileStorageService fileStorageService,
            com.sdu.kgplatform.repository.BrowsingHistoryRepository browsingHistoryRepository) {
        this.graphRepository = graphRepository;
        this.userRepository = userRepository;
        this.nodeRepository = nodeRepository;
        this.relationshipRepository = relationshipRepository;
        this.categoryRepository = categoryRepository;
        this.fileStorageService = fileStorageService;
        this.browsingHistoryRepository = browsingHistoryRepository;
    }

    // ==================== 创建图谱 ====================

    /**
     * 创建新图谱
     */
    @Transactional
    public GraphDetailDto createGraph(Integer uploaderId, GraphCreateDto dto) {
        // 验证用户存在
        User uploader = userRepository.findById(uploaderId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + uploaderId));

        // 检查同名图谱
        if (graphRepository.existsByUploaderIdAndName(uploaderId, dto.getName())) {
            throw new IllegalArgumentException("您已有同名图谱: " + dto.getName());
        }

        // 创建图谱实体
        KnowledgeGraph graph = new KnowledgeGraph();
        graph.setUploaderId(uploaderId);
        graph.setName(dto.getName());
        graph.setDescription(dto.getDescription());
        graph.setCoverImage(dto.getCoverImage());
        graph.setIsCustomCover(dto.getIsCustomCover() != null ? dto.getIsCustomCover() : false);
        graph.setUploadDate(LocalDate.now());
        graph.setLastModified(LocalDateTime.now());

        // 设置状态，默认为草稿
        if (dto.getStatus() != null && !dto.getStatus().isEmpty()) {
            try {
                graph.setStatus(GraphStatus.valueOf(dto.getStatus().toUpperCase()));
            } catch (IllegalArgumentException e) {
                graph.setStatus(GraphStatus.DRAFT);
            }
        } else {
            graph.setStatus(GraphStatus.DRAFT);
        }

        // 初始化统计字段
        graph.setViewCount(0);
        graph.setDownloadCount(0);
        graph.setCollectCount(0);
        graph.setNodeCount(0);
        graph.setRelationCount(0);
        graph.setIsCacheValid(false);

        // 缓存相关字段初始为 null（导入数据后再更新）
        graph.setCachedFileFormat(null);
        graph.setCachedFilePath(null);
        graph.setCachedGenerationDatetime(null);

        // 设置图谱质量指标默认值
        graph.setDensity(java.math.BigDecimal.ZERO);
        graph.setRelationRichness(java.math.BigDecimal.ZERO);
        graph.setEntityRichness(java.math.BigDecimal.ZERO);

        // 生成分享链接
        graph.setShareLink(generateShareLink());

        if (dto.getCategoryId() != null) {
            graph.setCategoryId(dto.getCategoryId());
        }

        // 设置领域分类
        graph.setDomain(dto.getDomain() != null ? dto.getDomain() : "other");

        KnowledgeGraph saved = graphRepository.save(graph);
        return convertToDetailDto(saved, uploader.getUserName(), uploader.getAvatar());
    }

    // ==================== 查询图谱 ====================

    /**
     * 根据ID获取图谱详情
     */
    public GraphDetailDto getGraphById(Integer graphId) {
        KnowledgeGraph graph = graphRepository.findById(graphId)
                .orElseThrow(() -> new IllegalArgumentException("图谱不存在: " + graphId));

        String uploaderName = getUserName(graph.getUploaderId());
        String uploaderAvatar = getUserAvatar(graph.getUploaderId());
        return convertToDetailDto(graph, uploaderName, uploaderAvatar);
    }

    /**
     * 根据分享链接获取图谱
     */
    public GraphDetailDto getGraphByShareLink(String shareLink) {
        KnowledgeGraph graph = graphRepository.findByShareLink(shareLink);
        if (graph == null) {
            throw new IllegalArgumentException("分享链接无效");
        }
        String uploaderName = getUserName(graph.getUploaderId());
        String uploaderAvatar = getUserAvatar(graph.getUploaderId());
        return convertToDetailDto(graph, uploaderName, uploaderAvatar);
    }

    /**
     * 获取用户的图谱列表
     */
    public Page<GraphListDto> getUserGraphs(Integer uploaderId, int page, int size, String sortBy) {
        Pageable pageable = createPageable(page, size, sortBy);
        Page<KnowledgeGraph> graphs = graphRepository.findByUploaderId(uploaderId, pageable);
        // 既然是获取特定用户的图谱，这里无需批量查询，直接查一次用户即可
        String uploaderName = getUserName(uploaderId);
        return graphs.map(g -> convertToListDto(g, uploaderName));
    }

    /**
     * 获取用户特定状态的图谱
     */
    public Page<GraphListDto> getUserGraphsByStatus(Integer uploaderId, String status, int page, int size) {
        return getUserGraphsByStatus(uploaderId, status, null, page, size);
    }

    public Page<GraphListDto> getUserGraphsByStatus(Integer uploaderId, String status, Integer categoryId, int page,
            int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lastModified"));
        try {
            GraphStatus graphStatus = GraphStatus.valueOf(status.toUpperCase());
            Page<KnowledgeGraph> graphs;
            if (categoryId != null) {
                graphs = graphRepository.findByUploaderIdAndStatusAndCategoryId(uploaderId, graphStatus, categoryId,
                        pageable);
            } else {
                graphs = graphRepository.findByUploaderIdAndStatus(uploaderId, graphStatus, pageable);
            }
            String uploaderName = getUserName(uploaderId);
            return graphs.map(g -> convertToListDto(g, uploaderName));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("无效的状态: " + status);
        }
    }

    /**
     * 获取公开图谱列表（已发布状态）
     */
    public Page<GraphListDto> getPublicGraphs(int page, int size, String sortBy) {
        return getPublicGraphs(page, size, sortBy, null);
    }

    public Page<GraphListDto> getPublicGraphs(int page, int size, String sortBy, Integer categoryId) {
        Pageable pageable = createPageable(page, size, sortBy);
        Page<KnowledgeGraph> graphs;
        if (categoryId != null) {
            graphs = graphRepository.findByStatusAndCategoryId(GraphStatus.PUBLISHED, categoryId, pageable);
        } else {
            graphs = graphRepository.findByStatus(GraphStatus.PUBLISHED, pageable);
        }
        return mapToGraphListDtos(graphs);
    }

    /**
     * 搜索公开图谱 (新版)
     */
    public Page<GraphListDto> searchPublicGraphs(com.sdu.kgplatform.dto.GraphSearchCriteria criteria,
            Pageable pageable) {
        // 创建 Specification
        org.springframework.data.jpa.domain.Specification<KnowledgeGraph> spec = com.sdu.kgplatform.repository.GraphSpecification
                .getSpec(criteria);
        // 执行查询
        Page<KnowledgeGraph> graphs = graphRepository.findAll(spec, pageable);
        return mapToGraphListDtos(graphs);
    }

    /**
     * 搜索公开图谱 (旧版 - 保留兼容)
     */
    public Page<GraphListDto> searchPublicGraphs(String keyword, int page, int size) {
        // 复用新版逻辑
        com.sdu.kgplatform.dto.GraphSearchCriteria criteria = new com.sdu.kgplatform.dto.GraphSearchCriteria();
        criteria.setKeyword(keyword);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "viewCount"));
        return searchPublicGraphs(criteria, pageable);
    }

    /**
     * 获取热门图谱
     */
    public List<GraphListDto> getPopularGraphs(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        List<KnowledgeGraph> graphs = graphRepository.findTopPopularGraphs(pageable);

        // 批量获取用户信息
        List<Integer> userIds = graphs.stream()
                .map(KnowledgeGraph::getUploaderId)
                .distinct()
                .toList();

        Map<Integer, String> userNames = new java.util.HashMap<>();
        if (!userIds.isEmpty()) {
            userRepository.findAllById(userIds).forEach(user -> userNames.put(user.getUserId(), user.getUserName()));
        }

        return graphs.stream()
                .map(g -> convertToListDto(g, userNames.getOrDefault(g.getUploaderId(), "未知用户")))
                .toList();
    }

    /**
     * 获取图谱可视化数据（轻量级 - 阶段一优化）
     */
    public Map<String, Object> getGraphVisualization(Integer graphId) {
        KnowledgeGraph graph = graphRepository.findById(graphId)
                .orElseThrow(() -> new IllegalArgumentException("图谱不存在: " + graphId));

        // 1. 获取轻量级节点
        List<com.sdu.kgplatform.dto.LiteNodeDto> nodes = nodeRepository.findLiteNodesByGraphId(graphId);

        // 2. 获取轻量级关系
        List<com.sdu.kgplatform.dto.LiteRelationshipDto> relations = relationshipRepository
                .findLiteRelationshipsByGraphId(graphId);

        // 3. 获取分类名称
        String categoryName = getCategoryName(graph.getCategoryId());

        // 4. 组装返回
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("nodes", nodes);
        result.put("links", relations);
        result.put("count", Map.of(
                "nodes", nodes.size(),
                "links", relations.size()));
        result.put("name", graph.getName());
        result.put("description", graph.getDescription());
        result.put("categoryId", graph.getCategoryId());
        result.put("categoryName", categoryName);
        result.put("domain", graph.getDomain());
        return result;
    }

    // ==================== 更新图谱 ====================

    /**
     * 更新图谱信息
     */
    @Transactional
    public GraphDetailDto updateGraph(Integer graphId, Integer userId, GraphUpdateDto dto) {
        KnowledgeGraph graph = graphRepository.findById(graphId)
                .orElseThrow(() -> new IllegalArgumentException("图谱不存在: " + graphId));

        // 验证权限
        if (!graph.getUploaderId().equals(userId)) {
            throw new IllegalArgumentException("无权修改此图谱");
        }

        // 更新字段
        if (dto.getName() != null && !dto.getName().trim().isEmpty()) {
            // 检查新名称是否与其他图谱冲突
            if (!graph.getName().equals(dto.getName()) &&
                    graphRepository.existsByUploaderIdAndName(userId, dto.getName())) {
                throw new IllegalArgumentException("您已有同名图谱: " + dto.getName());
            }
            graph.setName(dto.getName().trim());
        }

        if (dto.getDescription() != null) {
            graph.setDescription(dto.getDescription().trim());
        }

        if (dto.getCoverImage() != null) {
            graph.setCoverImage(dto.getCoverImage());
        }

        if (dto.getStatus() != null && !dto.getStatus().isEmpty()) {
            try {
                graph.setStatus(GraphStatus.valueOf(dto.getStatus().toUpperCase()));
            } catch (IllegalArgumentException e) {
                // 忽略无效状态
            }
        }

        if (dto.getCategoryId() != null) {
            graph.setCategoryId(dto.getCategoryId());
        }

        graph.setLastModified(LocalDateTime.now());
        KnowledgeGraph saved = graphRepository.save(graph);
        return convertToDetailDto(saved, getUserName(saved.getUploaderId()), getUserAvatar(saved.getUploaderId()));
    }

    /**
     * 更新图谱封面
     * 
     * @param isCustomCover true=用户上传的封面，false=自动生成的缩略图
     */
    @Transactional
    public void updateGraphCover(Integer graphId, Integer userId, String coverUrl, boolean isCustomCover) {
        KnowledgeGraph graph = graphRepository.findById(graphId)
                .orElseThrow(() -> new IllegalArgumentException("图谱不存在: " + graphId));

        if (!graph.getUploaderId().equals(userId)) {
            throw new IllegalArgumentException("无权修改此图谱");
        }

        // 删除旧封面
        if (graph.getCoverImage() != null) {
            fileStorageService.deleteFile(graph.getCoverImage());
        }

        graph.setCoverImage(coverUrl);
        graph.setIsCustomCover(isCustomCover);
        graph.setLastModified(LocalDateTime.now());
        graphRepository.save(graph);
    }

    /**
     * 更新图谱状态
     */
    @Transactional
    public void updateGraphStatus(Integer graphId, Integer userId, String status) {
        KnowledgeGraph graph = graphRepository.findById(graphId)
                .orElseThrow(() -> new IllegalArgumentException("图谱不存在: " + graphId));

        if (!graph.getUploaderId().equals(userId)) {
            throw new IllegalArgumentException("无权修改此图谱");
        }

        try {
            graph.setStatus(GraphStatus.valueOf(status.toUpperCase()));
            graph.setLastModified(LocalDateTime.now());
            graphRepository.save(graph);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("无效的状态: " + status);
        }
    }

    /**
     * 增加浏览量
     */
    @Transactional
    public void incrementViewCount(Integer graphId) {
        KnowledgeGraph graph = graphRepository.findById(graphId).orElse(null);
        if (graph != null) {
            graph.setViewCount(graph.getViewCount() == null ? 1 : graph.getViewCount() + 1);
            graphRepository.save(graph);
        }
    }

    /**
     * 更新图谱统计信息（节点数、关系数）- 从Neo4j查询
     */
    @Transactional
    public void updateGraphStats(Integer graphId) {
        KnowledgeGraph graph = graphRepository.findById(graphId).orElse(null);
        if (graph != null) {
            long nodeCount = nodeRepository.countByGraphId(graphId);
            graph.setNodeCount((int) nodeCount);

            long relationCount = relationshipRepository.countByGraphId(graphId);
            graph.setRelationCount((int) relationCount);

            graph.setLastModified(LocalDateTime.now());
            graphRepository.save(graph);
        }
    }

    /**
     * 更新图谱统计信息（直接设置节点数和关系数）
     */
    @Transactional
    public void updateGraphStats(Integer graphId, int nodeCount, int relationCount) {
        KnowledgeGraph graph = graphRepository.findById(graphId).orElse(null);
        if (graph != null) {
            graph.setNodeCount(nodeCount);
            graph.setRelationCount(relationCount);
            graph.setLastModified(LocalDateTime.now());
            graphRepository.save(graph);
        }
    }

    // ==================== 删除图谱 ====================

    /**
     * 删除图谱
     */
    @Transactional
    public void deleteGraph(Integer graphId, Integer userId) {
        KnowledgeGraph graph = graphRepository.findById(graphId)
                .orElseThrow(() -> new IllegalArgumentException("图谱不存在: " + graphId));

        if (!graph.getUploaderId().equals(userId)) {
            throw new IllegalArgumentException("无权删除此图谱");
        }

        // 删除浏览历史（避免外键约束错误）
        browsingHistoryRepository.deleteByGraphId(graphId);

        // 删除 Neo4j 中的节点（级联删除关系）
        nodeRepository.deleteByGraphId(graphId);

        // 删除封面
        if (graph.getCoverImage() != null) {
            fileStorageService.deleteFile(graph.getCoverImage());
        }

        // 删除图谱元数据
        graphRepository.delete(graph);
    }

    /**
     * 管理员删除图谱（无需权限验证）
     */
    @Transactional
    public void adminDeleteGraph(Integer graphId) {
        KnowledgeGraph graph = graphRepository.findById(graphId)
                .orElseThrow(() -> new IllegalArgumentException("图谱不存在: " + graphId));

        // 删除浏览历史（避免外键约束错误）
        browsingHistoryRepository.deleteByGraphId(graphId);

        nodeRepository.deleteByGraphId(graphId);

        // 删除封面
        if (graph.getCoverImage() != null) {
            fileStorageService.deleteFile(graph.getCoverImage());
        }

        graphRepository.delete(graph);
    }

    // ==================== 批量操作 ====================

    /**
     * 批量更新图谱状态（上线/下线）
     * 
     * @param graphIds 图谱ID列表
     * @param userId   操作用户ID
     * @param status   目标状态 (PUBLISHED 上线 / DRAFT 下线)
     * @return 成功更新的数量
     */
    @Transactional
    public int batchUpdateStatus(List<Integer> graphIds, Integer userId, String status) {
        GraphStatus targetStatus;
        try {
            targetStatus = GraphStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("无效的状态: " + status);
        }

        int successCount = 0;
        for (Integer graphId : graphIds) {
            try {
                KnowledgeGraph graph = graphRepository.findById(graphId).orElse(null);
                if (graph != null && graph.getUploaderId().equals(userId)) {
                    graph.setStatus(targetStatus);
                    graph.setLastModified(LocalDateTime.now());
                    graphRepository.save(graph);
                    successCount++;
                }
            } catch (Exception e) {
                // 单个失败不影响其他
                System.err.println("批量更新状态失败，graphId=" + graphId + ": " + e.getMessage());
            }
        }
        return successCount;
    }

    /**
     * 批量删除图谱
     * 
     * @param graphIds 图谱ID列表
     * @param userId   操作用户ID
     * @return 成功删除的数量
     */
    @Transactional
    public int batchDeleteGraphs(List<Integer> graphIds, Integer userId) {
        int successCount = 0;
        for (Integer graphId : graphIds) {
            try {
                KnowledgeGraph graph = graphRepository.findById(graphId).orElse(null);
                if (graph != null && graph.getUploaderId().equals(userId)) {
                    // 删除 Neo4j 中的节点
                    try {
                        nodeRepository.deleteByGraphId(graphId);
                    } catch (Exception e) {
                        System.err.println("删除Neo4j节点失败，graphId=" + graphId + ": " + e.getMessage());
                    }
                    // 删除图谱元数据
                    graphRepository.delete(graph);
                    successCount++;
                }
            } catch (Exception e) {
                // 单个失败不影响其他
                System.err.println("批量删除失败，graphId=" + graphId + ": " + e.getMessage());
            }
        }
        return successCount;
    }

    // ==================== 统计方法 ====================

    /**
     * 获取用户图谱数量
     */
    public long countUserGraphs(Integer uploaderId) {
        return graphRepository.countByUploaderId(uploaderId);
    }

    /**
     * 检查用户是否拥有该图谱
     */
    public boolean isGraphOwner(Integer graphId, Integer userId) {
        return graphRepository.findById(graphId)
                .map(g -> g.getUploaderId().equals(userId))
                .orElse(false);
    }

    // ==================== 私有辅助方法 ====================

    private String getUserName(Integer userId) {
        if (userId == null)
            return "未知用户";
        return userRepository.findById(userId)
                .map(User::getUserName)
                .orElse("未知用户");
    }

    private String getUserAvatar(Integer userId) {
        if (userId == null)
            return null;
        return userRepository.findById(userId)
                .map(User::getAvatar)
                .orElse(null);
    }

    /**
     * 批量转换 Page<KnowledgeGraph>
     */
    private Page<GraphListDto> mapToGraphListDtos(Page<KnowledgeGraph> graphs) {
        if (graphs.isEmpty()) {
            return graphs.map(g -> convertToListDto(g, "未知用户"));
        }

        List<Integer> userIds = graphs.getContent().stream()
                .map(KnowledgeGraph::getUploaderId)
                .distinct()
                .toList();

        Map<Integer, String> userNames = new java.util.HashMap<>();
        if (!userIds.isEmpty()) {
            userRepository.findAllById(userIds).forEach(user -> userNames.put(user.getUserId(), user.getUserName()));
        }

        return graphs.map(g -> convertToListDto(g, userNames.getOrDefault(g.getUploaderId(), "未知用户")));
    }

    private String generateShareLink() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private Pageable createPageable(int page, int size, String sortBy) {
        Sort sort;
        if (sortBy == null || sortBy.isEmpty()) {
            sort = Sort.by(Sort.Direction.DESC, "lastModified");
        } else {
            switch (sortBy.toLowerCase()) {
                case "views", "viewCount" -> sort = Sort.by(Sort.Direction.DESC, "viewCount");
                case "hot" -> sort = Sort.by(Sort.Direction.DESC, "hotScore");
                case "collects" -> sort = Sort.by(Sort.Direction.DESC, "collectCount");
                case "name" -> sort = Sort.by(Sort.Direction.ASC, "name");
                case "date" -> sort = Sort.by(Sort.Direction.DESC, "uploadDate");
                default -> sort = Sort.by(Sort.Direction.DESC, "lastModified");
            }
        }
        return PageRequest.of(page, size, sort);
    }

    private GraphDetailDto convertToDetailDto(KnowledgeGraph graph, String uploaderName, String uploaderAvatar) {
        return GraphDetailDto.builder()
                .graphId(graph.getGraphId())
                .uploaderId(graph.getUploaderId())
                .uploaderName(uploaderName)
                .uploaderAvatar(uploaderAvatar)
                .name(graph.getName())
                .description(graph.getDescription())
                .status(graph.getStatus() != null ? graph.getStatus().name() : null)
                .uploadDate(graph.getUploadDate())
                .lastModified(graph.getLastModified())
                .coverImage(graph.getCoverImage())
                .isCustomCover(graph.getIsCustomCover())
                .shareLink(graph.getShareLink())
                .viewCount(graph.getViewCount())
                .downloadCount(graph.getDownloadCount())
                .collectCount(graph.getCollectCount())
                .nodeCount(graph.getNodeCount())
                .relationCount(graph.getRelationCount())
                .density(graph.getDensity())
                .relationRichness(graph.getRelationRichness())
                .entityRichness(graph.getEntityRichness())
                .cachedFilePath(graph.getCachedFilePath())
                .cachedFileFormat(graph.getCachedFileFormat() != null ? graph.getCachedFileFormat().name() : null)
                .cachedGenerationDatetime(graph.getCachedGenerationDatetime())
                .isCacheValid(graph.getIsCacheValid())
                .categoryId(graph.getCategoryId())
                .categoryName(getCategoryName(graph.getCategoryId()))
                .domain(graph.getDomain())
                .build();
    }

    private String getCategoryName(Integer categoryId) {
        if (categoryId == null)
            return null;
        return categoryRepository.findById(categoryId)
                .map(com.sdu.kgplatform.entity.Category::getName)
                .orElse(null);
    }

    private GraphListDto convertToListDto(KnowledgeGraph graph, String uploaderName) {
        return GraphListDto.builder()
                .graphId(graph.getGraphId())
                .name(graph.getName())
                .description(graph.getDescription())
                .status(graph.getStatus() != null ? graph.getStatus().name() : null)
                .coverImage(graph.getCoverImage())
                .isCustomCover(graph.getIsCustomCover())
                .uploadDate(graph.getUploadDate())
                .uploaderId(graph.getUploaderId())
                .uploaderName(uploaderName)
                .viewCount(graph.getViewCount())
                .collectCount(graph.getCollectCount())
                .nodeCount(graph.getNodeCount())
                .relationCount(graph.getRelationCount())
                .downloadCount(graph.getDownloadCount())
                .density(graph.getDensity())
                .categoryId(graph.getCategoryId())
                .categoryName(getCategoryName(graph.getCategoryId()))
                .domain(graph.getDomain())
                .build();
    }

    /**
     * 个性化推荐图谱
     * 已登录用户：基于浏览历史的领域偏好 + hotScore 混合排序
     * 未登录/无记录：降级为 hotScore 排序
     */
    public Page<GraphListDto> getRecommendedGraphs(Integer userId, String domain, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        // 如果指定了领域筛选
        if (domain != null && !domain.isEmpty() && !"all".equals(domain)) {
            if (userId == null) {
                // 未登录 + 领域筛选：按 hotScore 排序
                Page<KnowledgeGraph> graphs = graphRepository.findByHotScoreAndDomain(domain, pageable);
                return mapToGraphListDtos(graphs);
            }
        }

        // 未登录：降级为热门
        if (userId == null) {
            Page<KnowledgeGraph> graphs = graphRepository.findByHotScore(pageable);
            return mapToGraphListDtos(graphs);
        }

        // 获取用户最近浏览的图谱记录
        java.util.List<com.sdu.kgplatform.entity.BrowsingHistory> history = browsingHistoryRepository
                .findTop50ByUserIdAndResourceTypeOrderByViewTimeDesc(userId,
                        com.sdu.kgplatform.entity.ResourceType.graph);

        if (history.isEmpty()) {
            // 无浏览记录：降级为热门
            if (domain != null && !domain.isEmpty() && !"all".equals(domain)) {
                Page<KnowledgeGraph> graphs = graphRepository.findByHotScoreAndDomain(domain, pageable);
                return mapToGraphListDtos(graphs);
            }
            Page<KnowledgeGraph> graphs = graphRepository.findByHotScore(pageable);
            return mapToGraphListDtos(graphs);
        }

        // 统计领域偏好频次
        java.util.Map<String, Long> domainFreq = new java.util.HashMap<>();
        java.util.List<Integer> viewedGraphIds = new java.util.ArrayList<>();
        for (com.sdu.kgplatform.entity.BrowsingHistory h : history) {
            if (h.getGraphId() != null) {
                viewedGraphIds.add(h.getGraphId());
                graphRepository.findById(h.getGraphId()).ifPresent(g -> {
                    String d = g.getDomain();
                    if (d != null && !"other".equals(d)) {
                        domainFreq.merge(d, 1L, Long::sum);
                    }
                });
            }
        }

        // 取偏好领域 Top 3
        java.util.List<String> preferredDomains = domainFreq.entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(3)
                .map(java.util.Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toList());

        // 如果指定了领域筛选，且该领域不在偏好中，也加入
        if (domain != null && !domain.isEmpty() && !"all".equals(domain)) {
            if (!preferredDomains.contains(domain)) {
                preferredDomains.add(0, domain);
            }
        }

        if (preferredDomains.isEmpty()) {
            // 无有效偏好：降级为热门
            Page<KnowledgeGraph> graphs = graphRepository.findByHotScore(pageable);
            return mapToGraphListDtos(graphs);
        }

        // 排除已浏览的图谱（提升新鲜度），但如果排除后太少则不排除
        java.util.List<Integer> excludeIds = viewedGraphIds.isEmpty()
                ? java.util.List.of(-1)
                : viewedGraphIds;

        Page<KnowledgeGraph> graphs = graphRepository.findRecommendedGraphs(preferredDomains, excludeIds, pageable);

        // 如果排除后结果太少，不排除再查一次
        if (graphs.getTotalElements() < size) {
            graphs = graphRepository.findRecommendedGraphs(preferredDomains, java.util.List.of(-1), pageable);
        }

        return mapToGraphListDtos(graphs);
    }

    /**
     * 更新所有图谱的热度分 (Hacker News 算法)
     */
    @Transactional
    public void updateAllHotScores() {
        List<KnowledgeGraph> allGraphs = graphRepository.findAll();
        for (KnowledgeGraph graph : allGraphs) {
            double score = calculateHotScore(graph);
            graph.setHotScore(score);
        }
        graphRepository.saveAll(allGraphs);
    }

    private double calculateHotScore(KnowledgeGraph graph) {
        // 1. 获取互动数据
        long views = graph.getViewCount() == null ? 0 : graph.getViewCount();
        long collects = graph.getCollectCount() == null ? 0 : graph.getCollectCount();
        long downloads = graph.getDownloadCount() == null ? 0 : graph.getDownloadCount();

        // 2. 计算互动总分 (权重: 收藏x5, 下载x3, 浏览x1)
        double interactions = views * 1.0 + collects * 5.0 + downloads * 3.0;

        // 3. 获取时间间隔 (天)
        long daysSinceUpload = 0;
        if (graph.getUploadDate() != null) {
            daysSinceUpload = java.time.Duration.between(
                    graph.getUploadDate().atStartOfDay(),
                    LocalDateTime.now()).toDays();
        }

        // +2 防止新发布时分母过小; +1 防止零互动图谱分数完全一致
        double t = Math.max(0, daysSinceUpload) + 2.0;
        double gravity = 1.5; // 重力因子 (越小衰减越慢)

        return (interactions + 1) / Math.pow(t, gravity);
    }
}
