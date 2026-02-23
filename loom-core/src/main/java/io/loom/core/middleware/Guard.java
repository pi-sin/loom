package io.loom.core.middleware;

public interface Guard {
    boolean canActivate(LoomHttpContext context);
}
