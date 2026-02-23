package io.loom.example.api;

import io.loom.core.annotation.LoomApi;
import io.loom.core.annotation.LoomGraph;
import io.loom.core.annotation.LoomQueryParam;
import io.loom.core.annotation.Node;
import io.loom.example.builder.*;
import io.loom.example.dto.UserDashboardResponse;
@LoomApi(
    method = "GET",
    path = "/api/users/{userId}/dashboard",
    response = UserDashboardResponse.class,
    summary = "Get user dashboard",
    description = "Aggregates user profile, recent orders and notifications into a single dashboard response",
    tags = {"Users"},
    queryParams = {
        @LoomQueryParam(name = "includeOrders", type = Boolean.class, description = "Whether to include recent orders", defaultValue = "true"),
        @LoomQueryParam(name = "notificationLimit", type = Integer.class, description = "Max number of notifications to return", defaultValue = "10")
    }
)
@LoomGraph({
    @Node(builder = FetchUserProfileBuilder.class, timeoutMs = 3000),
    @Node(builder = FetchUserOrdersBuilder.class, timeoutMs = 3000),
    @Node(builder = FetchUserNotificationsBuilder.class, required = false, timeoutMs = 2000),
    @Node(builder = AssembleDashboardBuilder.class,
          dependsOn = {FetchUserProfileBuilder.class, FetchUserOrdersBuilder.class,
                       FetchUserNotificationsBuilder.class})
})
public class UserDashboardApi {
}
