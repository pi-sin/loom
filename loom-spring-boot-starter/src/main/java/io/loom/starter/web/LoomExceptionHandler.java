package io.loom.starter.web;

import io.loom.core.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@ControllerAdvice
public class LoomExceptionHandler {

    @ExceptionHandler(LoomValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(LoomValidationException ex) {
        log.warn("[Loom] Validation failed: {}", ex.getMessage(), ex);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Validation Failed");
        body.put("message", ex.getMessage());
        body.put("violations", ex.getViolations());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(BuilderTimeoutException.class)
    public ResponseEntity<Map<String, Object>> handleTimeout(BuilderTimeoutException ex) {
        log.error("[Loom] Builder timeout: {}", ex.getMessage(), ex);
        return buildResponse(HttpStatus.GATEWAY_TIMEOUT, ex);
    }

    @ExceptionHandler(ServiceClientException.class)
    public ResponseEntity<Map<String, Object>> handleServiceClient(ServiceClientException ex) {
        log.error("[Loom] Service client error: {}", ex.getMessage(), ex);
        HttpStatus status = ex.getStatusCode() > 0
                ? HttpStatus.valueOf(ex.getStatusCode())
                : HttpStatus.BAD_GATEWAY;
        return buildResponse(status, ex);
    }

    @ExceptionHandler(LoomException.class)
    public ResponseEntity<Map<String, Object>> handleLoomException(LoomException ex) {
        log.error("[Loom] Error: {}", ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex);
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, LoomException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", ex.getMessage());
        if (ex.getRequestId() != null) {
            body.put("requestId", ex.getRequestId());
        }
        if (ex.getApiRoute() != null) {
            body.put("route", ex.getApiRoute());
        }
        return ResponseEntity.status(status).body(body);
    }
}
