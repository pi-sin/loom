package io.loom.example.dto;

import java.util.List;

public record UserNotifications(List<Notification> notifications) {
    public record Notification(String id, String message, boolean read) {}
}
