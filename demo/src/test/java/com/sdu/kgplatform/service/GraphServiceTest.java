package com.sdu.kgplatform.service;

import com.sdu.kgplatform.dto.GraphCreateDto;
import com.sdu.kgplatform.dto.GraphDetailDto;
import com.sdu.kgplatform.dto.GraphListDto;
import com.sdu.kgplatform.dto.GraphUpdateDto;
import com.sdu.kgplatform.entity.GraphStatus;
import com.sdu.kgplatform.entity.KnowledgeGraph;
import com.sdu.kgplatform.entity.User;
import com.sdu.kgplatform.repository.KnowledgeGraphRepository;
import com.sdu.kgplatform.repository.NodeRepository;
import com.sdu.kgplatform.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * GraphService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class GraphServiceTest {

    @Mock
    private KnowledgeGraphRepository graphRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NodeRepository nodeRepository;

    @InjectMocks
    private GraphService graphService;

    private User testUser;
    private KnowledgeGraph testGraph;

    @BeforeEach
    void setUp() {
        // 初始化测试用户
        testUser = new User();
        testUser.setUserId(1);
        testUser.setUserName("testuser");
        testUser.setEmail("test@example.com");

        // 初始化测试图谱
        testGraph = new KnowledgeGraph();
        testGraph.setGraphId(1);
        testGraph.setUploaderId(1);
        testGraph.setName("测试图谱");
        testGraph.setDescription("测试描述");
        testGraph.setStatus(GraphStatus.DRAFT);
        testGraph.setUploadDate(LocalDate.now());
        testGraph.setLastModified(LocalDateTime.now());
        testGraph.setViewCount(0);
        testGraph.setCollectCount(0);
        testGraph.setNodeCount(0);
        testGraph.setRelationCount(0);
        testGraph.setShareLink("abc123");
    }

    @Test
    @DisplayName("创建图谱 - 成功")
    void createGraph_Success() {
        // Arrange
        GraphCreateDto dto = new GraphCreateDto();
        dto.setName("新图谱");
        dto.setDescription("新描述");
        dto.setStatus("DRAFT");

        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(graphRepository.existsByUploaderIdAndName(1, "新图谱")).thenReturn(false);
        when(graphRepository.save(any(KnowledgeGraph.class))).thenAnswer(invocation -> {
            KnowledgeGraph saved = invocation.getArgument(0);
            saved.setGraphId(1);
            return saved;
        });

        // Act
        GraphDetailDto result = graphService.createGraph(1, dto);

        // Assert
        assertNotNull(result);
        assertEquals("新图谱", result.getName());
        assertEquals("新描述", result.getDescription());
        assertEquals("DRAFT", result.getStatus());
        verify(graphRepository, times(1)).save(any(KnowledgeGraph.class));
    }

    @Test
    @DisplayName("创建图谱 - 用户不存在")
    void createGraph_UserNotFound() {
        // Arrange
        GraphCreateDto dto = new GraphCreateDto();
        dto.setName("新图谱");

        when(userRepository.findById(999)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            graphService.createGraph(999, dto);
        });
    }

    @Test
    @DisplayName("创建图谱 - 同名图谱已存在")
    void createGraph_DuplicateName() {
        // Arrange
        GraphCreateDto dto = new GraphCreateDto();
        dto.setName("已存在的图谱");

        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(graphRepository.existsByUploaderIdAndName(1, "已存在的图谱")).thenReturn(true);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            graphService.createGraph(1, dto);
        });
    }

    @Test
    @DisplayName("获取图谱详情 - 成功")
    void getGraphById_Success() {
        // Arrange
        when(graphRepository.findById(1)).thenReturn(Optional.of(testGraph));
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));

        // Act
        GraphDetailDto result = graphService.getGraphById(1);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getGraphId());
        assertEquals("测试图谱", result.getName());
        assertEquals("testuser", result.getUploaderName());
    }

    @Test
    @DisplayName("获取图谱详情 - 图谱不存在")
    void getGraphById_NotFound() {
        // Arrange
        when(graphRepository.findById(999)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            graphService.getGraphById(999);
        });
    }

    @Test
    @DisplayName("获取用户图谱列表")
    void getUserGraphs_Success() {
        // Arrange
        Page<KnowledgeGraph> page = new PageImpl<>(List.of(testGraph));
        when(graphRepository.findByUploaderId(eq(1), any(Pageable.class))).thenReturn(page);
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));

        // Act
        Page<GraphListDto> result = graphService.getUserGraphs(1, 0, 10, null);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("测试图谱", result.getContent().get(0).getName());
    }

    @Test
    @DisplayName("更新图谱 - 成功")
    void updateGraph_Success() {
        // Arrange
        GraphUpdateDto dto = new GraphUpdateDto();
        dto.setName("更新后的名称");
        dto.setDescription("更新后的描述");

        when(graphRepository.findById(1)).thenReturn(Optional.of(testGraph));
        when(graphRepository.existsByUploaderIdAndName(1, "更新后的名称")).thenReturn(false);
        when(graphRepository.save(any(KnowledgeGraph.class))).thenReturn(testGraph);
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));

        // Act
        GraphDetailDto result = graphService.updateGraph(1, 1, dto);

        // Assert
        assertNotNull(result);
        verify(graphRepository, times(1)).save(any(KnowledgeGraph.class));
    }

    @Test
    @DisplayName("更新图谱 - 无权限")
    void updateGraph_NoPermission() {
        // Arrange
        GraphUpdateDto dto = new GraphUpdateDto();
        dto.setName("更新后的名称");

        when(graphRepository.findById(1)).thenReturn(Optional.of(testGraph));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            graphService.updateGraph(1, 999, dto); // 不同的用户ID
        });
    }

    @Test
    @DisplayName("删除图谱 - 成功")
    void deleteGraph_Success() {
        // Arrange
        when(graphRepository.findById(1)).thenReturn(Optional.of(testGraph));
        doNothing().when(nodeRepository).deleteByGraphId(1);
        doNothing().when(graphRepository).delete(testGraph);

        // Act
        graphService.deleteGraph(1, 1);

        // Assert
        verify(nodeRepository, times(1)).deleteByGraphId(1);
        verify(graphRepository, times(1)).delete(testGraph);
    }

    @Test
    @DisplayName("删除图谱 - 无权限")
    void deleteGraph_NoPermission() {
        // Arrange
        when(graphRepository.findById(1)).thenReturn(Optional.of(testGraph));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            graphService.deleteGraph(1, 999);
        });
    }

    @Test
    @DisplayName("增加浏览量")
    void incrementViewCount_Success() {
        // Arrange
        testGraph.setViewCount(5);
        when(graphRepository.findById(1)).thenReturn(Optional.of(testGraph));
        when(graphRepository.save(any(KnowledgeGraph.class))).thenReturn(testGraph);

        // Act
        graphService.incrementViewCount(1);

        // Assert
        assertEquals(6, testGraph.getViewCount());
        verify(graphRepository, times(1)).save(testGraph);
    }

    @Test
    @DisplayName("搜索公开图谱")
    void searchPublicGraphs_Success() {
        // Arrange
        testGraph.setStatus(GraphStatus.PUBLISHED);
        Page<KnowledgeGraph> page = new PageImpl<>(List.of(testGraph));
        when(graphRepository.searchPublicGraphs(eq("测试"), any(Pageable.class))).thenReturn(page);
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));

        // Act
        Page<GraphListDto> result = graphService.searchPublicGraphs("测试", 0, 10);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    @DisplayName("检查图谱所有权")
    void isGraphOwner_Success() {
        // Arrange
        when(graphRepository.findById(1)).thenReturn(Optional.of(testGraph));

        // Act & Assert
        assertTrue(graphService.isGraphOwner(1, 1));
        assertFalse(graphService.isGraphOwner(1, 999));
    }
}
