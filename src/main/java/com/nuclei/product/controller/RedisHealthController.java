package com.nuclei.product.controller;

import com.nuclei.product.service.IRedisCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/redis")
public class RedisHealthController {

    @Autowired
    private IRedisCacheService redisCacheService;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getRedisHealth() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean isAvailable = redisCacheService.isRedisAvailable();
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
        } catch (Exception e) {
            log.error("Error checking Redis health", e);
            response.put("status", "ERROR");
            response.put("redisAvailable", false);
            response.put("message", "Error checking Redis health: " + e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.status(500).body(response);
        }
    }

    @DeleteMapping("/cache/clear/products")
    public ResponseEntity<String> clearAllProductListCaches() {
        try {
            redisCacheService.invalidateAllProductLists();
            return ResponseEntity.ok("All product list caches cleared successfully");
        } catch (Exception e) {
            log.error("Failed to clear product list caches", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to clear product list caches: " + e.getMessage());
        }
    }

    @DeleteMapping("/cache/clear/product/{productId}")
    public ResponseEntity<String> clearProductCache(@PathVariable Long productId) {
        try {
            redisCacheService.invalidateProduct(productId);
            return ResponseEntity.ok("Product cache cleared successfully for ID: " + productId);
        } catch (Exception e) {
            log.error("Failed to clear product cache for ID: {}", productId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to clear product cache: " + e.getMessage());
        }
    }

    @GetMapping("/cache/stats")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean isAvailable = redisCacheService.isRedisAvailable();
            response.put("redisAvailable", isAvailable);
            response.put("timestamp", System.currentTimeMillis());
            
            if (isAvailable) {
                response.put("message", "Cache statistics retrieved successfully");
                // Additional cache statistics can be added here
            } else {
                response.put("message", "Redis not available for cache statistics");
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting cache statistics", e);
            response.put("status", "ERROR");
            response.put("message", "Error getting cache statistics: " + e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.status(500).body(response);
        }
    }
}
