package com.nuclei.product.util;

import com.nuclei.product.entity.ProductEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Redis cache utility for map-based serialization
 */
@Slf4j
@Component
public class RedisCacheUtil {

    private final RedisTemplate<String, Object> redisTemplate;

    // Cache key prefixes
    private static final String PRODUCT_PREFIX = "product";
    private static final String PRODUCT_LIST_PREFIX = "product-list";
    private static final String RESERVATION_PREFIX = "reservation";

    public RedisCacheUtil(final RedisTemplate<String, Object> redisTemplate) {

        this.redisTemplate = redisTemplate;
    }

    /**
     * Cache a product entity
     */
    public void cacheProduct(final Long productId, final ProductEntity product) {
        final String key = generateProductKey(productId);
        try {
            final Map<String, Object> productMap = convertProductToMap(product);
            redisTemplate.opsForValue().set(key, productMap);
            log.debug("Product cached successfully with key: {}", key);
        } catch (final Exception e) {
            log.error("Failed to cache product with key: {}", key, e);
            throw e;
        }
    }

    /**
     * Get cached product entity
     */
    public ProductEntity getCachedProduct(final Long productId) {
        final String key = generateProductKey(productId);
        try {
            final Object cached = redisTemplate.opsForValue().get(key);
            if (cached == null) {
                return null;
            }

            // Handle both old (direct ProductEntity) and new (map-based) cache formats
            if (cached instanceof ProductEntity) {
                return (ProductEntity) cached;
            } else if (cached instanceof Map) {
                @SuppressWarnings("unchecked")
                final Map<String, Object> productMap = (Map<String, Object>) cached;
                return convertMapToProduct(productMap);
            } else {
                log.warn("Unexpected cache format for product: {}", productId);
                return null;
            }
        } catch (final Exception e) {
            log.error("Failed to get cached product with key: {}", key, e);
            return null;
        }
    }

    /**
     * Cache a product page
     */
    public void cacheProductList(final String cacheKey, final Page<ProductEntity> productPage) {
        final String key = generateProductListKey(cacheKey);
        try {
            final Map<String, Object> pageMap = convertPageToMap(productPage);
            redisTemplate.opsForValue().set(key, pageMap);
            log.debug("Product list cached successfully with key: {}", key);
        } catch (final Exception e) {
            log.error("Failed to cache product list with key: {}", key, e);
            throw e;
        }
    }

    /**
     * Get cached product page
     */
    public Page<ProductEntity> getCachedProductList(final String cacheKey) {
        final String key = generateProductListKey(cacheKey);
        try {
            final Object cached = redisTemplate.opsForValue().get(key);
            if (cached == null) {
                return null;
            }

            // Handle both old (direct Page) and new (map-based) cache formats
            if (cached instanceof Page) {
                @SuppressWarnings("unchecked")
                final Page<ProductEntity> page = (Page<ProductEntity>) cached;
                return page;
            } else if (cached instanceof Map) {
                @SuppressWarnings("unchecked")
                final Map<String, Object> pageMap = (Map<String, Object>) cached;
                return convertMapToPage(pageMap);
            } else {
                log.warn("Unexpected cache format for product list: {}", cacheKey);
                return null;
            }
        } catch (final Exception e) {
            log.error("Failed to get cached product list with key: {}", key, e);
            return null;
        }
    }

    /**
     * Invalidate product cache
     */
    public void invalidateProduct(final Long productId) {
        final String key = generateProductKey(productId);
        try {
            redisTemplate.delete(key);
            log.debug("Product cache invalidated: {}", key);
        } catch (final Exception e) {
            log.error("Failed to invalidate product cache: {}", key, e);
        }
    }

    /**
     * Invalidate all product list caches
     */
    public void invalidateAllProductLists() {
        try {
            final String pattern = PRODUCT_LIST_PREFIX + ":*";
            final Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("Invalidated {} product list caches", keys.size());
            }
        } catch (final Exception e) {
            log.error("Failed to invalidate all product list caches", e);
        }
    }

    /**
     * Invalidate all product caches
     */
    public void invalidateAllProducts() {
        try {
            final String pattern = PRODUCT_PREFIX + ":*";
            final Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("Invalidated {} product caches", keys.size());
            }
        } catch (final Exception e) {
            log.error("Failed to invalidate all product caches", e);
        }
    }

    /**
     * Check if key exists
     */
    public boolean exists(final String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (final Exception e) {
            log.error("Failed to check key existence: {}", key, e);
            return false;
        }
    }

    /**
     * Set expiration for a key
     */
    public void setExpire(final String key, final Duration duration) {
        try {
            redisTemplate.expire(key, duration);
        } catch (final Exception e) {
            log.error("Failed to set expiration for key: {}", key, e);
        }
    }

    /**
     * Get expiration for a key
     */
    public Duration getExpire(final String key) {
        try {
            final Long ttl = redisTemplate.getExpire(key);
            return ttl != null ? Duration.ofSeconds(ttl) : Duration.ZERO;
        } catch (final Exception e) {
            log.error("Failed to get expiration for key: {}", key, e);
            return Duration.ZERO;
        }
    }

    /**
     * Generate product list cache key
     */
    public String generateProductListCacheKey(final int page, final int size, final boolean availableOnly, final Map<String, Object> filters) {
        final StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append("page:").append(page)
                 .append(":size:").append(size)
                 .append(":available:").append(availableOnly);
        
        if (filters != null && !filters.isEmpty()) {
            filters.forEach((filterKey, filterValue) -> 
                keyBuilder.append(":").append(filterKey).append(":").append(filterValue));
        }
        
        return keyBuilder.toString();
    }

    // Private helper methods

    private String generateProductKey(final Long productId) {

        return PRODUCT_PREFIX + ":" + productId;
    }

    private String generateProductListKey(final String cacheKey) {

        return PRODUCT_LIST_PREFIX + ":" + cacheKey;
    }

    /**
     * Convert ProductEntity to simple Map for caching
     */
    private Map<String, Object> convertProductToMap(final ProductEntity product) {
        final Map<String, Object> map = new HashMap<>();
        map.put("id", product.getId());
        map.put("name", product.getName());
        map.put("description", product.getDescription());
        map.put("priceAmount", product.getPriceAmount());
        map.put("priceCurrency", product.getPriceCurrency());
        map.put("stockQuantity", product.getStockQuantity());
        map.put("status", product.getStatus() != null ? product.getStatus().name() : "ACTIVE");
        map.put("version", product.getVersion());
        map.put("metadata", product.getMetadata());
        map.put("createdAt", product.getCreatedAt() != null ? product.getCreatedAt().toEpochMilli() : null);
        map.put("updatedAt", product.getUpdatedAt() != null ? product.getUpdatedAt().toEpochMilli() : null);
        
        return map;
    }

    /**
     * Convert Map back to ProductEntity
     */
    private ProductEntity convertMapToProduct(final Map<String, Object> map) {
        final ProductEntity product = new ProductEntity();
        product.setId((Long) map.get("id"));
        product.setName((String) map.get("name"));
        product.setDescription((String) map.get("description"));
        product.setPriceAmount((Double) map.get("priceAmount"));
        product.setPriceCurrency((String) map.get("priceCurrency"));
        product.setStockQuantity((Long) map.get("stockQuantity"));
        final String statusStr = (String) map.get("status");
        product.setStatus(statusStr != null ? com.nuclei.product.enums.ProductStatusEnums.valueOf(statusStr) : com.nuclei.product.enums.ProductStatusEnums.ACTIVE);
        product.setVersion((Long) map.get("version"));
        
        @SuppressWarnings("unchecked")
        final Map<String, String> metadata = (Map<String, String>) map.get("metadata");
        product.setMetadata(metadata);

        final Long createdAtMillis = (Long) map.get("createdAt");
        final Long updatedAtMillis = (Long) map.get("updatedAt");

        product.setCreatedAt(createdAtMillis != null ? java.time.Instant.ofEpochMilli(createdAtMillis) : null);
        product.setUpdatedAt(updatedAtMillis != null ? java.time.Instant.ofEpochMilli(updatedAtMillis) : null);
        
        return product;
    }

    /**
     * Convert Page<ProductEntity> to simple Map for caching
     */
    private Map<String, Object> convertPageToMap(final Page<ProductEntity> page) {
        final Map<String, Object> map = new HashMap<>();
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
    private Page<ProductEntity> convertMapToPage(final Map<String, Object> map) {
        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> contentMaps = (List<Map<String, Object>>) map.get("content");
        final List<ProductEntity> content = contentMaps.stream()
            .map(this::convertMapToProduct)
            .collect(Collectors.toList());
        
        final Pageable pageable = convertMapToPageable((Map<String, Object>) map.get("pageable"));
        final Long totalElements = (Long) map.get("totalElements");
        
        return new PageImpl<>(content, pageable, totalElements);
    }

    /**
     * Convert Pageable to simple Map
     */
    private Map<String, Object> convertPageableToMap(final Pageable pageable) {
        final Map<String, Object> map = new HashMap<>();
        map.put("pageNumber", pageable.getPageNumber());
        map.put("pageSize", pageable.getPageSize());
        map.put("sort", pageable.getSort() != null ? pageable.getSort().toString() : "");
        return map;
    }

    /**
     * Convert Map back to Pageable
     */
    private Pageable convertMapToPageable(final Map<String, Object> map) {
        final Integer pageNumber = (Integer) map.get("pageNumber");
        final Integer pageSize = (Integer) map.get("pageSize");
        return PageRequest.of(pageNumber, pageSize);
    }
}