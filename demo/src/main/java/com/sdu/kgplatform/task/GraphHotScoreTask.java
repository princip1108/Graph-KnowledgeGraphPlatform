package com.sdu.kgplatform.task;

import com.sdu.kgplatform.service.GraphService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 图谱热度分定时更新任务
 */
@Component
public class GraphHotScoreTask {

    private static final Logger log = LoggerFactory.getLogger(GraphHotScoreTask.class);

    private final GraphService graphService;

    public GraphHotScoreTask(GraphService graphService) {
        this.graphService = graphService;
    }

    /**
     * 每小时更新一次图谱热度分
     * fixedRate = 3600000 (1小时)
     */
    @Scheduled(fixedRate = 3600000)
    public void refreshHotScores() {
        log.info("开始更新图谱热度分...");
        try {
            graphService.updateAllHotScores();
            log.info("图谱热度分更新完成");
        } catch (Exception e) {
            log.error("图谱热度分更新失败", e);
        }
    }
}
