package com.nuclei.product.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuclei.product.entity.ProductEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
public class RedisCacheUtil {

    private static final String PRODUCT_PREFIX = "product:";
    private static final String PRODUCT_LIST_PREFIX = "product-list:";
    private static final String RESERVATION_PREFIX = "reservation:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisCacheUtil(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Cache a product entity as a simple map
     */
    public void cacheProduct(Long productId, ProductEntity product) {
        if (product == null) return;
        
        try {
            String key = PRODUCT_PREFIX + productId;
            Map<String, Object> productMap = convertProductToMap(product);
            redisTemplate.opsForValue().set(key, productMap);
            log.debug("Successfully cached product as map with key: {}", key);
        } catch (Exception e) {
            log.error("Failed to cache product with id: {}", productId, e);
        }
    }

    /**
     * Get cached product entity from map
     */
    public Optional<ProductEntity> getCachedProduct(Long productId) {
        try {
            String key = PRODUCT_PREFIX + productId;
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                log.debug("Cache hit for product key: {}", key);
                
                // Check if it's already a ProductEntity (from old cache format)
                if (cached instanceof ProductEntity) {
                    log.debug("Found cached ProductEntity directly, returning it");
                    return Optional.of((ProductEntity) cached);
                }
                
                // Check if it's a Map (from new cache format)
                if (cached instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> productMap = (Map<String, Object>) cached;
                    ProductEntity product = convertMapToProduct(productMap);
                    return Optional.of(product);
                }
                
                log.warn("Unexpected cached object type: {}", cached.getClass().getName());
                return Optional.empty();
            }
            log.debug("Cache miss for product key: {}", key);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to get cached product with id: {}", productId, e);
            return Optional.empty();
        }
    }

    /**
     * Cache a product page as a simple map structure
     */
    public void cacheProductList(String cacheKey, Page<ProductEntity> productPage) {
        if (productPage == null) return;
        
        try {
            String key = PRODUCT_LIST_PREFIX + cacheKey;
            Map<String, Object> pageMap = convertPageToMap(productPage);
            redisTemplate.opsForValue().set(key, pageMap);
            log.debug("Successfully cached product list as map with key: {}", key);
        } catch (Exception e) {
            log.error("Failed to cache product list with key: {}", cacheKey, e);
        }
    }

    /**
     * Get cached product page from map
     */
    public Optional<Page<ProductEntity>> getCachedProductList(String cacheKey) {
        try {
            String key = PRODUCT_LIST_PREFIX + cacheKey;
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                log.debug("Cache hit for product list key: {}", key);
                
                // Check if it's already a Page (from old cache format)
                if (cached instanceof Page) {
                    @SuppressWarnings("unchecked")
                    Page<ProductEntity> page = (Page<ProductEntity>) cached;
                    log.debug("Found cached Page directly, returning it");
                    return Optional.of(page);
                }
                
                // Check if it's a Map (from new cache format)
                if (cached instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> pageMap = (Map<String, Object>) cached;
                    Page<ProductEntity> productPage = convertMapToPage(pageMap);
                    return Optional.of(productPage);
                }
                
                log.warn("Unexpected cached object type: {}", cached.getClass().getName());
                return Optional.empty();
            }
            log.debug("Cache miss for product list key: {}", key);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to get cached product list with key: {}", cacheKey, e);
            return Optional.empty();
        }
    }

    /**
     * Invalidate specific product cache
     */
    public void invalidateProduct(Long productId) {
        try {
            String key = PRODUCT_PREFIX + productId;
            redisTemplate.delete(key);
            log.debug("Invalidated product cache for id: {}", productId);
        } catch (Exception e) {
            log.error("Failed to invalidate product cache for id: {}", productId, e);
        }
    }

    /**
     * Invalidate all product list caches
     */
    public void invalidateAllProductLists() {
        try {
            Set<String> keys = redisTemplate.keys(PRODUCT_LIST_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("Invalidated {} product list caches", keys.size());
            }
        } catch (Exception e) {
            log.error("Failed to invalidate product list caches", e);
        }
    }

    /**
     * Invalidate all product caches
     */
    public void invalidateAllProducts() {
        try {
            Set<String> keys = redisTemplate.keys(PRODUCT_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("Invalidated {} product caches", keys.size());
            }
        } catch (Exception e) {
            log.error("Failed to invalidate product caches", e);
        }
    }

    /**
     * Check if a key exists
     */
    public boolean exists(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("Failed to check key existence: {}", key, e);
            return false;
        }
    }

    /**
     * Set TTL for a key
     */
    public void setExpire(String key, long seconds) {
        try {
            redisTemplate.expire(key, seconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Failed to set TTL for key: {}", key, e);
        }
    }

    /**
     * Get TTL for a key
     */
    public long getExpire(String key) {
        try {
            Long ttl = redisTemplate.getExpire(key);
            return ttl != null ? ttl : -1;
        } catch (Exception e) {
            log.error("Failed to get TTL for key: {}", key, e);
            return -1;
        }
    }

    /**
     * Generate cache key for product list
     */
    public String generateProductListCacheKey(int page, int size, boolean availableOnly, Map<String, Object> filters) {
        StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append("page:").append(page);
        keyBuilder.append(":size:").append(size);
        keyBuilder.append(":available:").append(availableOnly);
        
        if (filters != null && !filters.isEmpty()) {
            keyBuilder.append(":filters:");
            filters.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> keyBuilder.append(entry.getKey()).append("=").append(entry.getValue()).append(":"));
        }
        
        return keyBuilder.toString();
    }

    /**
     * Convert ProductEntity to simple Map for caching
     */
    private Map<String, Object> convertProductToMap(ProductEntity product) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", product.getId());
        map.put("name", product.getName());
        map.put("description", product.getDescription());
        map.put("priceAmount", product.getPriceAmount());
        map.put("priceCurrency", product.getPriceCurrency());
        map.put("stockQuantity", product.getStockQuantity());
        map.put("status", product.getStatus() != null ? product.getStatus().name() : "ACTIVE");
        map.put("version", product.getVersion());
        map.put("metadata", product.getMetadata());
        
        // Store the actual timestamps from the entity
        // These should always be set by Hibernate @CreationTimestamp and @UpdateTimestamp
        map.put("createdAt", product.getCreatedAt().toEpochMilli());
        map.put("updatedAt", product.getUpdatedAt().toEpochMilli());
        
        return map;
    }

    /**
     * Convert Map back to ProductEntity
     */
    private ProductEntity convertMapToProduct(Map<String, Object> map) {
        ProductEntity product = new ProductEntity();
        product.setId((Long) map.get("id"));
        product.setName((String) map.get("name"));
        product.setDescription((String) map.get("description"));
        product.setPriceAmount((Double) map.get("priceAmount"));
        product.setPriceCurrency((String) map.get("priceCurrency"));
        product.setStockQuantity((Long) map.get("stockQuantity"));
        product.setStatus(com.nuclei.product.enums.ProductStatusEnums.valueOf((String) map.get("status")));
        product.setVersion((Long) map.get("version"));
        
        @SuppressWarnings("unchecked")
        Map<String, String> metadata = (Map<String, String>) map.get("metadata");
        product.setMetadata(metadata);
        
        // Convert timestamps back from milliseconds to Instant
        Long createdAtMillis = (Long) map.get("createdAt");
        Long updatedAtMillis = (Long) map.get("updatedAt");
        
        product.setCreatedAt(java.time.Instant.ofEpochMilli(createdAtMillis));
        product.setUpdatedAt(java.time.Instant.ofEpochMilli(updatedAtMillis));
        
        return product;
    }

    /**
     * Convert Page<ProductEntity> to simple Map for caching
     */
    private Map<String, Object> convertPageToMap(Page<ProductEntity> page) {
        Map<String, Object> map = new HashMap<>();
        map.put("content", page.getContent().stream()
            .map(this::convertProductToMap)
            .collect(Collectors.toList()));
        map.put("pageable", convertPageableToMap(page.getPageable()));
        map.put("totalElements", page.getTotalElements());
        map.put("totalPages", page.getTotalPages());
        map.put("last", page.isLast());
        map.put("first", page.isFirst());
        map.put("size", page.getSize());
        map.put("number", page.getNumber());
        map.put("numberOfElements", page.getNumberOfElements());
        map.put("empty", page.isEmpty());
        return map;
    }

    /**
     * Convert Map back to Page<ProductEntity>
     */
    private Page<ProductEntity> convertMapToPage(Map<String, Object> map) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> contentMaps = (List<Map<String, Object>>) map.get("content");
        List<ProductEntity> content = contentMaps.stream()
            .map(this::convertMapToProduct)
            .collect(Collectors.toList());
        
        Pageable pageable = convertMapToPageable((Map<String, Object>) map.get("pageable"));
        Long totalElements = (Long) map.get("totalElements");
        
        return new PageImpl<>(content, pageable, totalElements);
    }

    /**
     * Convert Pageable to simple Map
     */
    private Map<String, Object> convertPageableToMap(Pageable pageable) {
        Map<String, Object> map = new HashMap<>();
        map.put("pageNumber", pageable.getPageNumber());
        map.put("pageSize", pageable.getPageSize());
        map.put("offset", pageable.getOffset());
        map.put("paged", pageable.isPaged());
        map.put("unpaged", pageable.isUnpaged());
        return map;
    }

    /**
     * Convert Map back to Pageable
     */
    private Pageable convertMapToPageable(Map<String, Object> map) {
        int pageNumber = (Integer) map.get("pageNumber");
        int pageSize = (Integer) map.get("pageSize");
        return PageRequest.of(pageNumber, pageSize);
    }
}