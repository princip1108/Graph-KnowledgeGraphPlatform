package com.sdu.kgplatform.entity;

public enum UserStatus {
    ONLINE("在线"),
    OFFLINE("离线"),
    BANNED("封禁中"),
    DELETED("已注销");

    private final String description;

    UserStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
