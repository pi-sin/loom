package io.loom.example.api;

import io.loom.core.annotation.LoomApi;
import io.loom.core.annotation.LoomProxy;

@LoomApi(method = "GET",
         path = "/api/health",
         summary = "Health check",
         tags = {"Infrastructure"})
@LoomProxy(service = "health-service", route = "health-check")
public class HealthCheckApi {}
