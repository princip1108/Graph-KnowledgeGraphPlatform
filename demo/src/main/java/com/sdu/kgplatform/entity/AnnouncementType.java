package com.sdu.kgplatform.entity;

/**
 * 公告类型枚举
 */
public enum AnnouncementType {
    INFO("通知"),
    SUCCESS("好消息"),
    WARNING("警告"),
    ERROR("紧急");

    private final String description;

    AnnouncementType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
