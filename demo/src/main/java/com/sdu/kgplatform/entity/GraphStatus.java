package com.sdu.kgplatform.entity;

/**
 * 知识图谱状态枚举
 */
public enum GraphStatus {
    DRAFT("草稿"),
    PUBLISHED("已发布"),
    PRIVATE("私有"),
    DELETED("已删除");

    private final String description;

    GraphStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
