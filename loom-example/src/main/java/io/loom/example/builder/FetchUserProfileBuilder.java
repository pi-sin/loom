package io.loom.example.builder;

import io.loom.core.builder.BuilderContext;
import io.loom.core.builder.LoomBuilder;
import io.loom.example.dto.UserProfile;
import org.springframework.stereotype.Component;

@Component
public class FetchUserProfileBuilder implements LoomBuilder<UserProfile> {

    @Override
    public UserProfile build(BuilderContext context) {
        String userId = context.getPathVariable("userId");
        // In a real app: return context.service("user-service").get("/users/" + userId, UserProfile.class);
        return new UserProfile(userId, "John Doe", "john@example.com");
    }
}
