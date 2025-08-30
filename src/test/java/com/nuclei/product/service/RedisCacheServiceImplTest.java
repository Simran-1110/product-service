package com.nuclei.product.service;

import com.nuclei.product.entity.ProductEntity;
import com.nuclei.product.enums.ProductStatusEnums;
import com.nuclei.product.service.impl.RedisCacheServiceImpl;
import com.nuclei.product.util.RedisCacheUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RedisCacheServiceImplTest {

    @Mock
    private RedisCacheUtil redisCacheUtil;

    @InjectMocks
    private RedisCacheServiceImpl redisCacheService;

    private ProductEntity testProduct;
    private Page<ProductEntity> testPage;

    @BeforeEach
    void setUp() {
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

        testPage = new PageImpl<>(List.of(testProduct), PageRequest.of(0, 10), 1);
    }

    @Test
    void cacheProduct_success() {
        redisCacheService.cacheProduct(testProduct);
        verify(redisCacheUtil).cacheProduct(eq(1L), eq(testProduct), any());
    }

    @Test
    void getCachedProduct_cacheHit_returnsProduct() {
        when(redisCacheUtil.getProduct(1L, ProductEntity.class))
            .thenReturn(Optional.of(testProduct));

        Optional<ProductEntity> result = redisCacheService.getCachedProduct(1L);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(testProduct);
    }

    @Test
    void invalidateProductCache_success() {
        redisCacheService.invalidateProductCache(1L);
        verify(redisCacheUtil).invalidateProduct(1L);
    }

    @Test
    void onProductModified_invalidatesProductAndListCaches() {
        redisCacheService.onProductModified(1L);
        verify(redisCacheUtil).invalidateProduct(1L);
        verify(redisCacheUtil).invalidateAllProductLists();
    }
}
