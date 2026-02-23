package io.loom.example.dto;

import java.util.List;

public record UserDashboardResponse(
    UserProfile profile,
    UserOrders orders,
    UserNotifications notifications
) {}
