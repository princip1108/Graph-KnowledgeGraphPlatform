package com.sdu.kgplatform.service;

import com.sdu.kgplatform.dto.RelationshipBatchCreateResult;
import com.sdu.kgplatform.dto.RelationshipDto;
import com.sdu.kgplatform.repository.KnowledgeGraphRepository;
import com.sdu.kgplatform.repository.RelationshipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.neo4j.driver.Driver;
import org.springframework.data.neo4j.core.Neo4jClient;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelationshipServiceTest {

    @Mock
    private RelationshipRepository relationshipRepository;

    @Mock
    private KnowledgeGraphRepository graphRepository;

    @Mock
    private Neo4jClient neo4jClient;

    @Mock
    private Driver neo4jDriver;

    private RelationshipService relationshipService;

    @BeforeEach
    void setUp() {
        relationshipService = new RelationshipService(relationshipRepository, graphRepository, neo4jClient, neo4jDriver);
    }

    @Test
    void createRelationshipsBatch_RejectsOversizedBatchBeforeWriting() {
        when(graphRepository.existsById(1)).thenReturn(true);
        List<RelationshipDto> dtos = IntStream.range(0, 501)
                .mapToObj(i -> RelationshipDto.builder()
                        .sourceNodeId("source-" + i)
                        .targetNodeId("target-" + i)
                        .build())
                .toList();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> relationshipService.createRelationshipsBatch(1, dtos));

        assertTrue(ex.getMessage().contains("500"));
        verifyNoInteractions(neo4jDriver);
    }

    @Test
    void createRelationshipsBatch_ReturnsFailedItemsForInvalidRowsWithoutWriting() {
        when(graphRepository.existsById(1)).thenReturn(true);

        RelationshipBatchCreateResult result = relationshipService.createRelationshipsBatch(1, List.of(
                RelationshipDto.builder().sourceNodeId(" ").targetNodeId("target").build(),
                RelationshipDto.builder().sourceNodeId("source").targetNodeId(null).build()));

        assertFalse(result.isSuccess());
        assertFalse(result.isAllSucceeded());
        assertEquals(2, result.getRequestedCount());
        assertEquals(0, result.getSuccessCount());
        assertEquals(2, result.getFailedCount());
        assertEquals(2, result.getFailedItems().size());
        verifyNoInteractions(neo4jDriver);
    }
}
