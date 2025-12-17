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
import java.util.UUID;

/**
 * 知识图谱服务层
 */
@Service
public class GraphService {

    private final KnowledgeGraphRepository graphRepository;
    private final UserRepository userRepository;
    private final NodeRepository nodeRepository;

    public GraphService(KnowledgeGraphRepository graphRepository,
                        UserRepository userRepository,
                        NodeRepository nodeRepository) {
        this.graphRepository = graphRepository;
        this.userRepository = userRepository;
        this.nodeRepository = nodeRepository;
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
        return graphs.map(g -> convertToListDto(g, getUserName(g.getUploaderId())));
    }

    /**
     * 获取用户特定状态的图谱
     */
    public Page<GraphListDto> getUserGraphsByStatus(Integer uploaderId, String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lastModified"));
        try {
            GraphStatus graphStatus = GraphStatus.valueOf(status.toUpperCase());
            Page<KnowledgeGraph> graphs = graphRepository.findByUploaderIdAndStatus(uploaderId, graphStatus, pageable);
            return graphs.map(g -> convertToListDto(g, getUserName(g.getUploaderId())));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("无效的状态: " + status);
        }
    }

    /**
     * 获取公开图谱列表（已发布状态）
     */
    public Page<GraphListDto> getPublicGraphs(int page, int size, String sortBy) {
        Pageable pageable = createPageable(page, size, sortBy);
        Page<KnowledgeGraph> graphs = graphRepository.findByStatus(GraphStatus.PUBLISHED, pageable);
        return graphs.map(g -> convertToListDto(g, getUserName(g.getUploaderId())));
    }

    /**
     * 搜索公开图谱
     */
    public Page<GraphListDto> searchPublicGraphs(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "viewCount"));
        Page<KnowledgeGraph> graphs = graphRepository.searchPublicGraphs(keyword, pageable);
        return graphs.map(g -> convertToListDto(g, getUserName(g.getUploaderId())));
    }

    /**
     * 获取热门图谱
     */
    public List<GraphListDto> getPopularGraphs(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        List<KnowledgeGraph> graphs = graphRepository.findTopPopularGraphs(pageable);
        return graphs.stream()
                .map(g -> convertToListDto(g, getUserName(g.getUploaderId())))
                .toList();
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

        graph.setLastModified(LocalDateTime.now());
        KnowledgeGraph saved = graphRepository.save(graph);
        return convertToDetailDto(saved, getUserName(saved.getUploaderId()), getUserAvatar(saved.getUploaderId()));
    }

    /**
     * 更新图谱封面
     */
    @Transactional
    public void updateGraphCover(Integer graphId, Integer userId, String coverUrl) {
        KnowledgeGraph graph = graphRepository.findById(graphId)
                .orElseThrow(() -> new IllegalArgumentException("图谱不存在: " + graphId));

        if (!graph.getUploaderId().equals(userId)) {
            throw new IllegalArgumentException("无权修改此图谱");
        }

        graph.setCoverImage(coverUrl);
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
            // 关系数需要 RelationshipRepository，暂时跳过
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

        // 删除 Neo4j 中的节点（级联删除关系）
        nodeRepository.deleteByGraphId(graphId);

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

        nodeRepository.deleteByGraphId(graphId);
        graphRepository.delete(graph);
    }

    // ==================== 批量操作 ====================

    /**
     * 批量更新图谱状态（上线/下线）
     * @param graphIds 图谱ID列表
     * @param userId 操作用户ID
     * @param status 目标状态 (PUBLISHED 上线 / DRAFT 下线)
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
     * @param graphIds 图谱ID列表
     * @param userId 操作用户ID
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
        if (userId == null) return "未知用户";
        return userRepository.findById(userId)
                .map(User::getUserName)
                .orElse("未知用户");
    }

    private String getUserAvatar(Integer userId) {
        if (userId == null) return null;
        return userRepository.findById(userId)
                .map(User::getAvatar)
                .orElse(null);
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
                case "views" -> sort = Sort.by(Sort.Direction.DESC, "viewCount");
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
                .build();
    }

    private GraphListDto convertToListDto(KnowledgeGraph graph, String uploaderName) {
        return GraphListDto.builder()
                .graphId(graph.getGraphId())
                .name(graph.getName())
                .description(graph.getDescription())
                .status(graph.getStatus() != null ? graph.getStatus().name() : null)
                .coverImage(graph.getCoverImage())
                .uploadDate(graph.getUploadDate())
                .uploaderId(graph.getUploaderId())
                .uploaderName(uploaderName)
                .viewCount(graph.getViewCount())
                .collectCount(graph.getCollectCount())
                .nodeCount(graph.getNodeCount())
                .relationCount(graph.getRelationCount())
                .build();
    }
}
