package com.nuclei.product.service;

import com.nuclei.product.dao.IProductDao;
import com.nuclei.product.entity.ProductEntity;
import com.nuclei.product.enums.ProductStatusEnums;
import com.nuclei.product.service.impl.RedisCacheServiceImpl;
import com.nuclei.product.util.RedisCacheUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisCacheServiceImplTest {

    @Mock
    private RedisCacheUtil redisCacheUtil;

    @Mock
    private IProductDao productDao;

    private RedisCacheServiceImpl redisCacheService;

    private ProductEntity testProduct;
    private Page<ProductEntity> testPage;

    @BeforeEach
    void setUp() {
        redisCacheService = new RedisCacheServiceImpl(redisCacheUtil);
        
        testProduct = ProductEntity.builder()
            .id(1L)
            .name("Test Product")
            .description("Test Description")
            .priceAmount(10.0)
            .priceCurrency("INR")
            .stockQuantity(5L)
            .status(ProductStatusEnums.ACTIVE)
            .version(1L)
            .build();
        
        // Set timestamps to avoid null pointer issues
        testProduct.setCreatedAt(Instant.now());
        testProduct.setUpdatedAt(Instant.now());

        testPage = new PageImpl<>(List.of(testProduct), PageRequest.of(0, 10), 1);
    }

    @Test
    void cacheProduct_success() {
        redisCacheService.cacheProduct(1L, testProduct);
        verify(redisCacheUtil).cacheProduct(eq(1L), eq(testProduct));
    }

    @Test
    void getCachedProduct_cacheHit_returnsProduct() {
        when(redisCacheUtil.getCachedProduct(1L))
            .thenReturn(testProduct);

        ProductEntity result = redisCacheService.getCachedProduct(1L);

        assertThat(result).isEqualTo(testProduct);
    }

    @Test
    void getCachedProduct_cacheMiss_returnsNull() {
        when(redisCacheUtil.getCachedProduct(1L))
            .thenReturn(null);

        ProductEntity result = redisCacheService.getCachedProduct(1L);

        assertThat(result).isNull();
    }

    @Test
    void cacheProductList_success() {
        redisCacheService.cacheProductList("test-key", testPage);
        verify(redisCacheUtil).cacheProductList(eq("test-key"), eq(testPage));
    }

    @Test
    void getCachedProductList_cacheHit_returnsPage() {
        when(redisCacheUtil.getCachedProductList("test-key"))
            .thenReturn(testPage);

        Page<ProductEntity> result = redisCacheService.getCachedProductList("test-key");

        assertThat(result).isEqualTo(testPage);
    }

    @Test
    void invalidateProduct_success() {
        redisCacheService.invalidateProduct(1L);
        verify(redisCacheUtil).invalidateProduct(1L);
    }

    @Test
    void invalidateAllProductLists_success() {
        redisCacheService.invalidateAllProductLists();
        verify(redisCacheUtil).invalidateAllProductLists();
    }

    @Test
    void invalidateAllProducts_success() {
        redisCacheService.invalidateAllProducts();
        verify(redisCacheUtil).invalidateAllProducts();
    }

    @Test
    void isRedisAvailable_success() {
        when(redisCacheUtil.exists("health-check")).thenReturn(true);

        boolean result = redisCacheService.isRedisAvailable();

        assertThat(result).isTrue();
    }

    @Test
    void isRedisAvailable_failure() {
        when(redisCacheUtil.exists("health-check")).thenThrow(new RuntimeException("Connection failed"));

        boolean result = redisCacheService.isRedisAvailable();

        assertThat(result).isFalse();
    }

    @Test
    void onProductModified_invalidatesProductAndListCaches() {
        redisCacheService.onProductModified(1L);
        verify(redisCacheUtil).invalidateProduct(1L);
        verify(redisCacheUtil).invalidateAllProductLists();
    }

    @Test
    void onProductDeleted_invalidatesProductAndListCaches() {
        redisCacheService.onProductDeleted(1L);
        verify(redisCacheUtil).invalidateProduct(1L);
        verify(redisCacheUtil).invalidateAllProductLists();
    }

    @Test
    void onStockModified_invalidatesListCachesOnly() {
        redisCacheService.onStockModified(1L);
        verify(redisCacheUtil).invalidateAllProductLists();
        verify(redisCacheUtil, never()).invalidateProduct(any());
    }
}
