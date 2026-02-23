package io.loom.example.api;

import io.loom.core.annotation.LoomApi;
import io.loom.core.annotation.LoomUpstream;

@LoomApi(method = "GET",
         path = "/api/health",
         summary = "Health check",
         tags = {"Infrastructure"})
@LoomUpstream(name = "health-service", path = "/internal/health")
public class HealthCheckApi {}
