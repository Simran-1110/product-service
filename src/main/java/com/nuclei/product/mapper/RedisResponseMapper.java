package com.nuclei.product.mapper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Mapper for building Redis-related responses
 * Centralizes all response building logic for Redis operations
 */
@Slf4j
@Component
public class RedisResponseMapper {

    /**
     * Build health check response
     */
    public ResponseEntity<Map<String, Object>> buildHealthResponse(final boolean isAvailable) {
        final Map<String, Object> response = new HashMap<>();
        response.put("status", isAvailable ? "UP" : "DOWN");
        response.put("redisAvailable", isAvailable);
        response.put("timestamp", System.currentTimeMillis());
        
        if (isAvailable) {
            response.put("message", "Redis is available and responding");
            return ResponseEntity.ok(response);
        } else {
            response.put("message", "Redis is not available");
            return ResponseEntity.status(503).body(response);
        }
    }

    /**
     * Build error response for health check
     */
    public ResponseEntity<Map<String, Object>> buildHealthErrorResponse(final Exception e) {
        final Map<String, Object> response = new HashMap<>();
        response.put("status", "ERROR");
        response.put("redisAvailable", false);
        response.put("message", "Error checking Redis health: " + e.getMessage());
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.status(500).body(response);
    }

    /**
     * Build success response for cache clearing
     */
    public ResponseEntity<String> buildCacheClearSuccessResponse() {
        return ResponseEntity.ok("All product list caches cleared successfully");
    }

    /**
     * Build error response for cache clearing
     */
    public ResponseEntity<String> buildCacheClearErrorResponse(final Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body("Failed to clear product list caches: " + e.getMessage());
    }

    /**
     * Build success response for specific product cache clearing
     */
    public ResponseEntity<String> buildProductCacheClearSuccessResponse(final Long productId) {
        return ResponseEntity.ok("Product cache cleared successfully for ID: " + productId);
    }

    /**
     * Build error response for specific product cache clearing
     */
    public ResponseEntity<String> buildProductCacheClearErrorResponse(final Long productId, final Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body("Failed to clear product cache: " + e.getMessage());
    }

    /**
     * Build cache statistics response
     */
    public ResponseEntity<Map<String, Object>> buildCacheStatsResponse(final boolean isAvailable) {
        final Map<String, Object> response = new HashMap<>();
        response.put("redisAvailable", isAvailable);
        response.put("timestamp", System.currentTimeMillis());
        
        if (isAvailable) {
            response.put("message", "Cache statistics retrieved successfully");
            // Additional cache statistics can be added here
        } else {
            response.put("message", "Redis not available for cache statistics");
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * Build error response for cache statistics
     */
    public ResponseEntity<Map<String, Object>> buildCacheStatsErrorResponse(final Exception e) {
        final Map<String, Object> response = new HashMap<>();
        response.put("status", "ERROR");
        response.put("message", "Error getting cache statistics: " + e.getMessage());
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.status(500).body(response);
    }
}
