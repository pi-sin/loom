package io.loom.example.builder;

import io.loom.core.builder.BuilderContext;
import io.loom.core.builder.LoomBuilder;
import io.loom.example.dto.UserNotifications;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FetchUserNotificationsBuilder implements LoomBuilder<UserNotifications> {

    @Override
    public UserNotifications build(BuilderContext context) {
        String userId = context.getPathVariable("userId");
        // In a real app: context.service("notification-service").get(...)
        return new UserNotifications(List.of(
                new UserNotifications.Notification("N1", "Your order has been shipped!", false),
                new UserNotifications.Notification("N2", "New product recommendation", true)
        ));
    }
}
