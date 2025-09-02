package com.nuclei.product.service.impl;

import com.nuclei.product.dao.IProductDao;
import com.nuclei.product.dao.IReservationDao;
import com.nuclei.product.dto.CreateProductDto;
import com.nuclei.product.dto.UpdateProductDto;
import com.nuclei.product.dto.ListProductsDto;
import com.nuclei.product.dto.ReserveStockDto;
import com.nuclei.product.dto.ConfirmReservationDto;
import com.nuclei.product.dto.ReleaseReservationDto;
import com.nuclei.product.entity.ProductEntity;
import com.nuclei.product.entity.ReservationEntity;
import com.nuclei.product.enums.ProductStatusEnums;
import com.nuclei.product.exception.InsufficientStockException;
import com.nuclei.product.exception.NotFoundException;
import com.nuclei.product.mapper.ProductProtoMapper;
import com.nuclei.product.service.IProductService;
import com.nuclei.product.service.IRedisCacheService;
import com.nuclei.product.util.ProductSpecificationBuilder;
import com.nuclei.product.validation.ProductValidator;
import com.nuclei.product.validation.ReservationValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Product service implementation
 */
@Slf4j
@Service
public class ProductServiceImpl implements IProductService {

    private final IProductDao productDao;
    private final IReservationDao reservationDao;
    private final ProductProtoMapper productMapper;
    private final ProductValidator productValidator;
    private final ReservationValidator reservationValidator;
    private final IRedisCacheService redisCacheService;
    private final ProductSpecificationBuilder specificationBuilder;

    public ProductServiceImpl(final IProductDao productDao,
                              final IReservationDao reservationDao,
                              final ProductProtoMapper productMapper,
                              final ProductValidator productValidator,
                              final ReservationValidator reservationValidator,
                              final IRedisCacheService redisCacheService,
                              final ProductSpecificationBuilder specificationBuilder) {
        this.productDao = productDao;
        this.reservationDao = reservationDao;
        this.productMapper = productMapper;
        this.productValidator = productValidator;
        this.reservationValidator = reservationValidator;
        this.redisCacheService = redisCacheService;
        this.specificationBuilder = specificationBuilder;
    }

    @Override
    @Transactional
    public ProductEntity createProduct(final CreateProductDto request) {
        productValidator.validateCreate(request);
        final ProductEntity product = productMapper.toEntity(request);
        
        if (product.getStockQuantity() == null) {
            product.setStockQuantity(0L);
        }
        
        final ProductEntity savedProduct = productDao.saveAndFlush(product);

        redisCacheService.cacheProduct(savedProduct.getId(), savedProduct);
        
        return savedProduct;
    }

    @Override
    public Optional<ProductEntity> getProductById(final Long id) {
        // Try cache first
        final ProductEntity cachedProduct = redisCacheService.getCachedProduct(id);
        if (cachedProduct != null) {
            return Optional.of(cachedProduct);
        }
        
        // Cache miss - get from database
        final Optional<ProductEntity> product = productDao.findById(id);
        if (product.isPresent()) {
            // Cache the product for future requests
            redisCacheService.cacheProduct(product.get().getId(), product.get());
        }
        
        return product;
    }

    @Override
    @Transactional
    public ProductEntity updateProduct(final UpdateProductDto request) {
        productValidator.validateUpdate(request);
        final ProductEntity existingProduct = productDao.findById(request.getId())
            .orElseThrow(() -> new NotFoundException("product not found: " + request.getId()));

        // Update entity using mapper
        productMapper.updateEntity(existingProduct, request);

        if (request.getExpectedVersion() != null && !Objects.equals(request.getExpectedVersion(), existingProduct.getVersion())) {
            throw new IllegalStateException("version mismatch");
        }

        final ProductEntity updatedProduct = productDao.saveAndFlush(existingProduct);
        
        // Invalidate caches when product is modified
        redisCacheService.onProductModified(existingProduct.getId());
        
        return updatedProduct;
    }

    @Override
    @Transactional
    public ProductEntity deleteProduct(final Long id) {
        final ProductEntity existingProduct = productDao.findById(id)
            .orElseThrow(() -> new NotFoundException("product not found: " + id));
        
        // Soft delete: mark as DISCONTINUED instead of hard delete
        existingProduct.setStatus(ProductStatusEnums.DISCONTINUED);
        final ProductEntity deletedProduct = productDao.saveAndFlush(existingProduct);
        
        // Invalidate caches when product is deleted
        redisCacheService.onProductDeleted(id);
        
        return deletedProduct;
    }

    @Override
    public Page<ProductEntity> listProducts(final ListProductsDto criteria) {
        // Generate cache key for this specific query
        final Map<String, Object> filters = buildFilters(criteria);
        final String cacheKey = redisCacheService.generateProductListCacheKey(
            criteria.getPage(), 
            criteria.getPageSize(), 
            criteria.getOnlyAvailable(), 
            filters
        );
        
        // Try cache first
        final Page<ProductEntity> cachedResult = redisCacheService.getCachedProductList(cacheKey);
        if (cachedResult != null) {
            return cachedResult;
        }
        
        // Cache miss - query database
        final Pageable pageable = PageRequest.of(
            Math.max(0, criteria.getPage() - 1), 
            Math.max(1, criteria.getPageSize()), 
            Sort.by("id").ascending()
        );
        
        // Build dynamic specification for filtering
        final Specification<ProductEntity> spec = specificationBuilder.buildSpecification(criteria);
        final Page<ProductEntity> result = productDao.findAll(spec, pageable);
        
        // Cache the result for future requests
        redisCacheService.cacheProductList(cacheKey, result);
        
        return result;
    }

    @Override
    @Transactional
    public ReservationEntity reserveStock(final ReserveStockDto request) {
        reservationValidator.validateReserve(request);

        // Idempotency check
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isEmpty()) {
            final Optional<ReservationEntity> existingReservation = reservationDao.findByIdempotencyKey(request.getIdempotencyKey());
            if (existingReservation.isPresent()) {
                return existingReservation.get();
            }
        }

        final ProductEntity product = productDao.findByIdForUpdate(request.getProductId())
            .orElseThrow(() -> new NotFoundException("product not found: " + request.getProductId()));

        // Check if product is available for reservation
        if (product.getStatus() == ProductStatusEnums.DISCONTINUED) {
            throw new IllegalStateException("cannot reserve stock for discontinued product: " + request.getProductId());
        }

        if (request.getExpectedVersion() != null && !Objects.equals(request.getExpectedVersion(), product.getVersion())) {
            throw new IllegalStateException("version mismatch");
        }

        if (product.getStockQuantity() < request.getQuantity()) {
            throw new InsufficientStockException("insufficient stock for product: " + request.getProductId());
        }

        product.setStockQuantity(product.getStockQuantity() - request.getQuantity());
        productDao.save(product);

        final String reservationId = UUID.randomUUID().toString();
        final ReservationEntity reservation = ReservationEntity.builder()
            .reservationId(reservationId)
            .productId(request.getProductId())
            .quantity(request.getQuantity())
            .status("IN_PROGRESS")
            .idempotencyKey(request.getIdempotencyKey())
            .orderId(null)
            .build();

        final Integer ttlSeconds = request.getTtlSeconds();
        if (ttlSeconds != null && ttlSeconds > 0) {
            reservation.setTtlExpiresAt(Instant.now().plus(ttlSeconds, ChronoUnit.SECONDS));
        }

        final ReservationEntity savedReservation = reservationDao.save(reservation);
        
        // Invalidate list caches when stock changes (but not product metadata caches)
        redisCacheService.onStockModified(request.getProductId());
        
        return savedReservation;
    }

    @Override
    @Transactional
    public ReservationEntity confirmReservation(final ConfirmReservationDto request) {
        reservationValidator.validateConfirm(request);
        final ReservationEntity reservation = reservationDao.findByReservationId(request.getReservationId())
            .orElseThrow(() -> new NotFoundException("reservation not found: " + request.getReservationId()));

        if ("CONFIRMED".equals(reservation.getStatus())) {
            return reservation;
        }
        if ("RELEASED".equals(reservation.getStatus())) {
            throw new IllegalStateException("reservation already released");
        }

        reservation.setStatus("CONFIRMED");
        if (request.getOrderId() != null) {
            reservation.setOrderId(request.getOrderId());
        }
        return reservationDao.save(reservation);
    }

    @Override
    @Transactional
    public ReservationEntity releaseReservation(final ReleaseReservationDto request) {
        reservationValidator.validateRelease(request);
        final ReservationEntity reservation = reservationDao.findByReservationId(request.getReservationId())
            .orElseThrow(() -> new NotFoundException("reservation not found: " + request.getReservationId()));

        if ("RELEASED".equals(reservation.getStatus())) {
            return reservation;
        }

        final ProductEntity product = productDao.findByIdForUpdate(reservation.getProductId())
            .orElseThrow(() -> new NotFoundException("product not found: " + reservation.getProductId()));

        product.setStockQuantity(product.getStockQuantity() + reservation.getQuantity());
        productDao.save(product);

        // Mark reservation as released
        reservation.setStatus("RELEASED");
        reservationDao.save(reservation);
        
        // Invalidate list caches when stock changes (but not product metadata caches)
        redisCacheService.onStockModified(reservation.getProductId());
        
        return reservation;
    }

    /**
     * Build filters map for cache key generation
     */
    private Map<String, Object> buildFilters(final ListProductsDto criteria) {
        final Map<String, Object> filters = new HashMap<>();
        if (criteria.getMinPriceAmount() != null) {
            filters.put("minPrice", criteria.getMinPriceAmount());
        }
        if (criteria.getMaxPriceAmount() != null) {
            filters.put("maxPrice", criteria.getMaxPriceAmount());
        }
        return filters;
    }
}

