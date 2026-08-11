package com.sdu.kgplatform.service;

import com.sdu.kgplatform.entity.BrowsingHistory;
import com.sdu.kgplatform.entity.ResourceType;
import com.sdu.kgplatform.repository.BrowsingHistoryRepository;
import com.sdu.kgplatform.repository.SearchHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoryServiceTest {

    @Mock
    private SearchHistoryRepository searchHistoryRepository;

    @Mock
    private BrowsingHistoryRepository browsingHistoryRepository;

    private HistoryService historyService;

    @BeforeEach
    void setUp() {
        historyService = new HistoryService(searchHistoryRepository, browsingHistoryRepository);
    }

    @Test
    void recordBrowsingIfStale_ReturnsFalseInsideDedupWindow() {
        BrowsingHistory history = new BrowsingHistory();
        history.setUserId(1);
        history.setResourceType(ResourceType.graph);
        history.setGraphId(10);
        history.setViewTime(LocalDateTime.now().minusMinutes(5));
        when(browsingHistoryRepository.findByUserIdAndResourceTypeAndGraphId(1, ResourceType.graph, 10))
                .thenReturn(Optional.of(history));

        boolean shouldCount = historyService.recordBrowsingIfStale(1, ResourceType.graph, 10);

        assertFalse(shouldCount);
        verify(browsingHistoryRepository, never()).save(history);
    }

    @Test
    void recordBrowsingIfStale_UpdatesHistoryOutsideDedupWindow() {
        BrowsingHistory history = new BrowsingHistory();
        history.setUserId(1);
        history.setResourceType(ResourceType.post);
        history.setPostId(20);
        history.setViewTime(LocalDateTime.now().minusHours(1));
        when(browsingHistoryRepository.findByUserIdAndResourceTypeAndPostId(1, ResourceType.post, 20))
                .thenReturn(Optional.of(history));

        boolean shouldCount = historyService.recordBrowsingIfStale(1, ResourceType.post, 20);

        assertTrue(shouldCount);
        verify(browsingHistoryRepository).save(history);
    }

    @Test
    void recordBrowsingIfStale_AllowsAnonymousViewWithoutHistoryLookup() {
        boolean shouldCount = historyService.recordBrowsingIfStale(null, ResourceType.graph, 10);

        assertTrue(shouldCount);
        verifyNoInteractions(browsingHistoryRepository);
    }
}
