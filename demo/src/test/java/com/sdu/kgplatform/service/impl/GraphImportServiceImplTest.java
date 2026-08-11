package com.sdu.kgplatform.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sdu.kgplatform.dto.GraphDetailDto;
import com.sdu.kgplatform.service.FileStorageService;
import com.sdu.kgplatform.service.FileValidationService;
import com.sdu.kgplatform.service.GraphImportBatchWriter;
import com.sdu.kgplatform.service.GraphService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GraphImportServiceImplTest {

    @Mock
    private GraphService graphService;

    @Mock
    private GraphImportBatchWriter graphImportBatchWriter;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private FileValidationService fileValidationService;

    private GraphImportServiceImpl importService;

    @BeforeEach
    void setUp() {
        importService = new GraphImportServiceImpl(
                graphService,
                graphImportBatchWriter,
                fileStorageService,
                fileValidationService,
                new ObjectMapper());
    }

    @Test
    @SuppressWarnings("unchecked")
    void importGraph_UsesBatchWriterAndSingleStatsUpdate() {
        String json = """
                {
                  "name": "导入图谱",
                  "description": "导入描述",
                  "nodes": [
                    {"name": "A", "type": "Person", "description": "alpha"},
                    {"name": "B", "type": "Company"}
                  ],
                  "relations": [
                    {"source": "A", "target": "B", "type": "works_at"}
                  ]
                }
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "graph.json", "application/json", json.getBytes(StandardCharsets.UTF_8));
        when(graphService.createGraph(eq(7), any()))
                .thenReturn(GraphDetailDto.builder().graphId(99).name("导入图谱").build());
        when(graphImportBatchWriter.writeGraph(eq(99), any(), any()))
                .thenReturn(new GraphImportBatchWriter.BatchWriteResult(2, 1));

        GraphDetailDto result = importService.importGraph(file, null, null, "DRAFT", "science", null, 7);

        ArgumentCaptor<List<GraphImportBatchWriter.ImportedNode>> nodesCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<GraphImportBatchWriter.ImportedRelationship>> relationshipsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(graphImportBatchWriter).writeGraph(eq(99), nodesCaptor.capture(), relationshipsCaptor.capture());
        verify(graphService).updateGraphStats(99, 2, 1);

        List<GraphImportBatchWriter.ImportedNode> nodes = nodesCaptor.getValue();
        List<GraphImportBatchWriter.ImportedRelationship> relationships = relationshipsCaptor.getValue();
        assertEquals(2, nodes.size());
        assertEquals(1, relationships.size());
        assertEquals("A", nodes.get(0).name());
        assertEquals("B", nodes.get(1).name());
        assertEquals(nodes.get(0).nodeId(), relationships.get(0).sourceNodeId());
        assertEquals(nodes.get(1).nodeId(), relationships.get(0).targetNodeId());
        assertNotNull(result);
        assertEquals(2, result.getNodeCount());
        assertEquals(1, result.getRelationCount());
    }
}
