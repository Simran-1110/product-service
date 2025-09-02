package com.nuclei.product.service.impl;

import com.nuclei.product.dao.IProductDao;
import com.nuclei.product.entity.ProductEntity;
import com.nuclei.product.enums.ProductStatusEnums;
import com.nuclei.product.service.IRedisCacheService;
import com.nuclei.product.util.RedisCacheUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Redis cache service implementation
 */
@Slf4j
@Service
public class RedisCacheServiceImpl implements IRedisCacheService {

    private final RedisCacheUtil redisCacheUtil;

    @Value("${cache.product.ttl:7200}")
    private int productCacheTtl;

    @Value("${cache.product-list.ttl:1800}")
    private int productListCacheTtl;

    public RedisCacheServiceImpl(final RedisCacheUtil redisCacheUtil) {
        this.redisCacheUtil = redisCacheUtil;
    }

    @Override
    public void cacheProduct(final Long productId, final ProductEntity product) {
        if (product == null || product.getStatus() == ProductStatusEnums.DISCONTINUED) {
            log.debug("Skipping cache for null or discontinued product: {}", productId);
            return;
        }

        // Ensure the product has been saved to database with valid timestamps
        if (product.getCreatedAt() == null || product.getUpdatedAt() == null) {
            log.debug("Skipping cache for product {} - timestamps not yet set by database", productId);
            return;
        }

        try {
            redisCacheUtil.cacheProduct(productId, product);
            log.debug("Successfully cached product: {}", productId);
        } catch (final Exception e) {
            log.error("Failed to cache product: {}", productId, e);
        }
    }

    @Override
    public ProductEntity getCachedProduct(final Long productId) {
        try {
            return redisCacheUtil.getCachedProduct(productId);
        } catch (final Exception e) {
            log.error("Failed to get cached product: {}", productId, e);
            return null;
        }
    }

    @Override
    public void cacheProductList(final String cacheKey, final Page<ProductEntity> productPage) {
        if (productPage == null || productPage.isEmpty()) {
            log.debug("Skipping cache for empty product page with key: {}", cacheKey);
            return;
        }

        try {
            redisCacheUtil.cacheProductList(cacheKey, productPage);
            log.debug("Successfully cached product list with key: {}", cacheKey);
        } catch (final Exception e) {
            log.error("Failed to cache product list with key: {}", cacheKey, e);
        }
    }

    @Override
    public Page<ProductEntity> getCachedProductList(final String cacheKey) {
        try {
            return redisCacheUtil.getCachedProductList(cacheKey);
        } catch (final Exception e) {
            log.error("Failed to get cached product list with key: {}", cacheKey, e);
            return null;
        }
    }

    @Override
    public void invalidateProduct(final Long productId) {
        try {
            redisCacheUtil.invalidateProduct(productId);
            log.debug("Successfully invalidated product cache: {}", productId);
        } catch (final Exception e) {
            log.error("Failed to invalidate product cache: {}", productId, e);
        }
    }

    @Override
    public void invalidateAllProductLists() {
        try {
            redisCacheUtil.invalidateAllProductLists();
            log.debug("Successfully invalidated all product list caches");
        } catch (final Exception e) {
            log.error("Failed to invalidate all product list caches", e);
        }
    }

    @Override
    public void invalidateAllProducts() {
        try {
            redisCacheUtil.invalidateAllProducts();
            log.debug("Successfully invalidated all product caches");
        } catch (final Exception e) {
            log.error("Failed to invalidate all product caches", e);
        }
    }

    @Override
    public String generateProductListCacheKey(
        final int page,
        final int size,
        final boolean availableOnly,
        final Map<String, Object> filters) {
        return redisCacheUtil.generateProductListCacheKey(page, size, availableOnly, filters);
    }

    @Override
    public boolean isRedisAvailable() {
        try {
            return redisCacheUtil.exists("health-check");
        } catch (final Exception e) {
            log.warn("Redis health check failed", e);
            return false;
        }
    }

    @Override
    public void onProductModified(final Long productId) {
        log.debug("Product modified, invalidating caches for product: {}", productId);
        invalidateProduct(productId);
        invalidateAllProductLists();
    }

    @Override
    public void onProductDeleted(final Long productId) {
        log.debug("Product deleted, invalidating caches for product: {}", productId);
        invalidateProduct(productId);
        invalidateAllProductLists();
    }

    @Override
    public void onStockModified(final Long productId) {
        log.debug("Stock modified, invalidating list caches for product: {}", productId);
        invalidateAllProductLists();
    }
}
