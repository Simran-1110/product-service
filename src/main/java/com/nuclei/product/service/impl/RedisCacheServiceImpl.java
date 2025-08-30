package com.nuclei.product.service.impl;

import com.nuclei.product.entity.ProductEntity;
import com.nuclei.product.service.IRedisCacheService;
import com.nuclei.product.util.RedisCacheUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class RedisCacheServiceImpl implements IRedisCacheService {

    private final RedisCacheUtil redisCacheUtil;

    @Value("${cache.product.ttl:7200}") // 2 hours default
    private long productCacheTtl;

    @Value("${cache.product-list.ttl:1800}") // 30 minutes default
    private long productListCacheTtl;

    public RedisCacheServiceImpl(RedisCacheUtil redisCacheUtil) {
        this.redisCacheUtil = redisCacheUtil;
    }

    @Override
    public void cacheProduct(Long productId, ProductEntity product) {
        if (product == null || product.getStatus() == com.nuclei.product.enums.ProductStatusEnums.DISCONTINUED) {
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
        } catch (Exception e) {
            log.error("Failed to cache product: {}", productId, e);
        }
    }

    @Override
    public Optional<ProductEntity> getCachedProduct(Long productId) {
        try {
            return redisCacheUtil.getCachedProduct(productId);
        } catch (Exception e) {
            log.error("Failed to get cached product: {}", productId, e);
            return Optional.empty();
        }
    }

    @Override
    public void cacheProductList(String cacheKey, Page<ProductEntity> productPage) {
        if (productPage == null || productPage.isEmpty()) {
            log.debug("Skipping cache for null or empty product page");
            return;
        }

        try {
            redisCacheUtil.cacheProductList(cacheKey, productPage);
            log.debug("Successfully cached product list with key: {}", cacheKey);
        } catch (Exception e) {
            log.error("Failed to cache product list with key: {}", cacheKey, e);
        }
    }

    @Override
    public Optional<Page<ProductEntity>> getCachedProductList(String cacheKey) {
        try {
            return redisCacheUtil.getCachedProductList(cacheKey);
        } catch (Exception e) {
            log.error("Failed to get cached product list with key: {}", cacheKey, e);
            return Optional.empty();
        }
    }

    @Override
    public void invalidateProduct(Long productId) {
        try {
            redisCacheUtil.invalidateProduct(productId);
            log.debug("Invalidated product cache: {}", productId);
        } catch (Exception e) {
            log.error("Failed to invalidate product cache: {}", productId, e);
        }
    }

    @Override
    public void invalidateAllProductLists() {
        try {
            redisCacheUtil.invalidateAllProductLists();
            log.debug("Invalidated all product list caches");
        } catch (Exception e) {
            log.error("Failed to invalidate all product list caches", e);
        }
    }

    @Override
    public void invalidateAllProducts() {
        try {
            redisCacheUtil.invalidateAllProducts();
            log.debug("Invalidated all product caches");
        } catch (Exception e) {
            log.error("Failed to invalidate all product caches", e);
        }
    }

    @Override
    public String generateProductListCacheKey(int page, int size, boolean availableOnly, Map<String, Object> filters) {
        return redisCacheUtil.generateProductListCacheKey(page, size, availableOnly, filters);
    }

    @Override
    public boolean isRedisAvailable() {
        try {
            // Try to perform a simple Redis operation
            redisCacheUtil.exists("health-check");
            return true;
        } catch (Exception e) {
            log.warn("Redis is not available: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void onProductModified(Long productId) {
        log.debug("Product modified, invalidating caches for product: {}", productId);
        invalidateProduct(productId);
        invalidateAllProductLists();
    }

    @Override
    public void onProductDeleted(Long productId) {
        log.debug("Product deleted, invalidating caches for product: {}", productId);
        invalidateProduct(productId);
        invalidateAllProductLists();
    }

    @Override
    public void onStockModified(Long productId) {
        log.debug("Stock modified, invalidating product list caches for product: {}", productId);
        // Only invalidate list caches as individual product cache is still valid
        invalidateAllProductLists();
    }
}
