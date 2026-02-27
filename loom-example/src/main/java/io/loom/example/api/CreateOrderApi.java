package io.loom.example.api;

import io.loom.core.annotation.LoomApi;
import io.loom.core.annotation.LoomHeaderParam;
import io.loom.core.annotation.LoomProxy;
import io.loom.example.dto.CreateOrderRequest;
import io.loom.example.dto.OrderResponse;
import io.loom.example.interceptor.ApiKeyInterceptor;

@LoomApi(method = "POST",
         path = "/api/orders",
         request = CreateOrderRequest.class,
         response = OrderResponse.class,
         interceptors = {ApiKeyInterceptor.class},
         summary = "Create a new order",
         tags = {"Orders"},
         headers = {@LoomHeaderParam(name = "X-API-Key", required = true, description = "API key")})
@LoomProxy(service = "order-service", route = "create-order")
public class CreateOrderApi {}
