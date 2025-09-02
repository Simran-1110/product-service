package com.nuclei.product.controller;

import com.nuclei.product.mapper.RedisResponseMapper;
import com.nuclei.product.service.IRedisCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Redis health and cache management controller
 */
@Slf4j
@RestController
@RequestMapping("/api/redis")
public class RedisHealthController {

    private final IRedisCacheService redisCacheService;
    private final RedisResponseMapper responseMapper;

    public RedisHealthController(final IRedisCacheService redisCacheService, final RedisResponseMapper responseMapper) {
        this.redisCacheService = redisCacheService;
        this.responseMapper = responseMapper;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getRedisHealth() {
        try {
            final boolean isAvailable = redisCacheService.isRedisAvailable();
            return responseMapper.buildHealthResponse(isAvailable);
        } catch (final Exception e) {
            log.error("Error checking Redis health", e);
            return responseMapper.buildHealthErrorResponse(e);
        }
    }

    @DeleteMapping("/cache/clear/products")
    public ResponseEntity<String> clearAllProductListCaches() {
        try {
            redisCacheService.invalidateAllProductLists();
            return responseMapper.buildCacheClearSuccessResponse();
        } catch (final Exception e) {
            log.error("Failed to clear product list caches", e);
            return responseMapper.buildCacheClearErrorResponse(e);
        }
    }

    @DeleteMapping("/cache/clear/product/{productId}")
    public ResponseEntity<String> clearProductCache(@PathVariable final Long productId) {
        try {
            redisCacheService.invalidateProduct(productId);
            return responseMapper.buildProductCacheClearSuccessResponse(productId);
        } catch (final Exception e) {
            log.error("Failed to clear product cache for ID: {}", productId, e);
            return responseMapper.buildProductCacheClearErrorResponse(productId, e);
        }
    }

    @GetMapping("/cache/stats")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        try {
            final boolean isAvailable = redisCacheService.isRedisAvailable();
            return responseMapper.buildCacheStatsResponse(isAvailable);
        } catch (final Exception e) {
            log.error("Error getting cache statistics", e);
            return responseMapper.buildCacheStatsErrorResponse(e);
        }
    }
}
