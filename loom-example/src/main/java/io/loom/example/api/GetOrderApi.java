package io.loom.example.api;

import io.loom.core.annotation.LoomApi;
import io.loom.core.annotation.LoomHeaderParam;
import io.loom.core.annotation.LoomProxy;
import io.loom.core.annotation.LoomQueryParam;
import io.loom.example.dto.OrderResponse;
import io.loom.example.interceptor.ApiKeyInterceptor;

@LoomApi(method = "GET",
         path = "/api/orders/{orderId}",
         response = OrderResponse.class,
         interceptors = {ApiKeyInterceptor.class},
         summary = "Get order by ID",
         tags = {"Orders"},
         queryParams = {
             @LoomQueryParam(name = "expand", description = "Comma-separated fields to expand")
         },
         headers = {@LoomHeaderParam(name = "X-API-Key", required = true, description = "API key")})
@LoomProxy(service = "order-service", route = "get-order")
public class GetOrderApi {}
