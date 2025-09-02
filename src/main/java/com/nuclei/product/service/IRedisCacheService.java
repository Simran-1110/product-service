package com.nuclei.product.service;

import com.nuclei.product.entity.ProductEntity;
import org.springframework.data.domain.Page;

import java.util.Map;

/**
 * Service interface for Redis-based caching operations
 * Implements Map-based caching: Proto Object → Simple Map → Jackson Serialization → Redis
 */
public interface IRedisCacheService {

    /**
     * Cache a product entity
     */
    void cacheProduct(final Long productId, final ProductEntity product);

    /**
     * Retrieve a cached product entity
     */
    ProductEntity getCachedProduct(final Long productId);

    /**
     * Cache a product page
     */
    void cacheProductList(final String cacheKey, final Page<ProductEntity> productPage);

    /**
     * Retrieve a cached product page
     */
    Page<ProductEntity> getCachedProductList(final String cacheKey);

    /**
     * Invalidate cache for a specific product
     */
    void invalidateProduct(final Long productId);

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
    String generateProductListCacheKey(final int page, final int size, final boolean availableOnly, final Map<String, Object> filters);

    /**
     * Check if Redis is available
     */
    boolean isRedisAvailable();

    /**
     * Invalidate caches when product is modified
     */
    void onProductModified(final Long productId);

    /**
     * Invalidate caches when product is deleted
     */
    void onProductDeleted(final Long productId);

    /**
     * Invalidate caches when stock is modified
     */
    void onStockModified(final Long productId);
}
