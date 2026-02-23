package io.loom.example.builder;

import io.loom.core.builder.BuilderContext;
import io.loom.core.builder.LoomBuilder;
import io.loom.example.dto.*;
import org.springframework.stereotype.Component;

@Component
public class AssembleDashboardBuilder implements LoomBuilder<UserDashboardResponse> {

    @Override
    public UserDashboardResponse build(BuilderContext context) {
        UserProfile profile = context.getDependency(UserProfile.class);
        UserOrders orders = context.getDependency(UserOrders.class);
        UserNotifications notifications = context.getDependency(UserNotifications.class);

        return new UserDashboardResponse(profile, orders, notifications);
    }
}
