package com.nuclei.product.service;

import com.nuclei.product.entity.ProductEntity;
import org.springframework.data.domain.Page;

import java.util.Map;
import java.util.Optional;

/**
 * Service interface for Redis-based caching operations
 * Implements Map-based caching: Proto Object → Simple Map → Jackson Serialization → Redis
 */
public interface IRedisCacheService {

    /**
     * Cache a product entity
     */
    void cacheProduct(Long productId, ProductEntity product);

    /**
     * Retrieve a cached product entity
     */
    Optional<ProductEntity> getCachedProduct(Long productId);

    /**
     * Cache a product page
     */
    void cacheProductList(String cacheKey, Page<ProductEntity> productPage);

    /**
     * Retrieve a cached product page
     */
    Optional<Page<ProductEntity>> getCachedProductList(String cacheKey);

    /**
     * Invalidate cache for a specific product
     */
    void invalidateProduct(Long productId);

    /**
     * Invalidate all product list caches
     */
    void invalidateAllProductLists();

    /**
     * Invalidate all product caches
     */
    void invalidateAllProducts();

    /**
     * Generate cache key for product list with filters
     */
    String generateProductListCacheKey(int page, int size, boolean availableOnly, Map<String, Object> filters);

    /**
     * Check if Redis is available
     */
    boolean isRedisAvailable();

    /**
     * Invalidate caches when product is modified
     */
    void onProductModified(Long productId);

    /**
     * Invalidate caches when product is deleted
     */
    void onProductDeleted(Long productId);

    /**
     * Invalidate caches when stock is modified
     */
    void onStockModified(Long productId);
}
