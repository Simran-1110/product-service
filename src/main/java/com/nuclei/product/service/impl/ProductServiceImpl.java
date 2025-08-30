package com.nuclei.product.service.impl;

import com.nuclei.product.dto.*;
import com.nuclei.product.entity.ProductEntity;
import com.nuclei.product.entity.ReservationEntity;
import com.nuclei.product.enums.ProductStatusEnums;
import com.nuclei.product.exception.InsufficientStockException;
import com.nuclei.product.exception.NotFoundException;
import com.nuclei.product.repository.ProductRepository;
import com.nuclei.product.repository.ReservationRepository;
import com.nuclei.product.service.IProductService;
import com.nuclei.product.service.IRedisCacheService;
import com.nuclei.product.validation.ProductValidator;
import com.nuclei.product.validation.ReservationValidator;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProductServiceImpl implements IProductService {

  private final ProductRepository productRepository;
  private final ReservationRepository reservationRepository;
  private final ProductValidator productValidator;
  private final ReservationValidator reservationValidator;
  private final IRedisCacheService redisCacheService;

  public ProductServiceImpl(final ProductRepository productRepository,
                            final ReservationRepository reservationRepository,
                            final ProductValidator productValidator,
                            final ReservationValidator reservationValidator,
                            final IRedisCacheService redisCacheService) {
    this.productRepository = productRepository;
    this.reservationRepository = reservationRepository;
    this.productValidator = productValidator;
    this.reservationValidator = reservationValidator;
    this.redisCacheService = redisCacheService;
  }

  @Override
  @Transactional
  public ProductEntity createProduct(final CreateProductDto request) {
    productValidator.validateCreate(request);
    final ProductEntity product = request.toEntity();
    if (product.getStockQuantity() == null) {
      product.setStockQuantity(0L);
    }
    ProductEntity saved = productRepository.saveAndFlush(product);
    
    // Cache the newly created product
    redisCacheService.cacheProduct(saved.getId(), saved);
    
    return saved;
  }

  @Override
  public Optional<ProductEntity> getProductById(final Long id) {
    // Try cache first
    Optional<ProductEntity> cached = redisCacheService.getCachedProduct(id);
    if (cached.isPresent()) {
      return cached;
    }
    
    // Cache miss - get from database
    Optional<ProductEntity> product = productRepository.findById(id);
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
    final ProductEntity existing = productRepository.findById(request.getId())
        .orElseThrow(() -> new NotFoundException("product not found: " + request.getId()));

    // update selective fields
    if (request.getName() != null) {
      existing.setName(request.getName());
    }
    if (request.getDescription() != null) {
      existing.setDescription(request.getDescription());
    }
    if (request.getPriceAmount() != null) {
      existing.setPriceAmount(request.getPriceAmount());
    }
    if (request.getPriceCurrency() != null) {
      existing.setPriceCurrency(request.getPriceCurrency());
    }
    if (request.getStockQuantity() != null) {
      existing.setStockQuantity(request.getStockQuantity());
    }
    if (request.getStatus() != null) {
      existing.setStatus(request.getStatus());
    }

    if (request.getExpectedVersion() != null && !Objects.equals(request.getExpectedVersion(), existing.getVersion())) {
      throw new IllegalStateException("version mismatch");
    }

    ProductEntity updated = productRepository.saveAndFlush(existing);
    
    // Invalidate caches when product is modified
    redisCacheService.onProductModified(existing.getId());
    
    return updated;
  }

  @Override
  @Transactional
  public ProductEntity deleteProduct(final Long id) {
    final ProductEntity existing = productRepository.findById(id)
        .orElseThrow(() -> new NotFoundException("product not found: " + id));
    
    // Soft delete: mark as DISCONTINUED instead of hard delete
    existing.setStatus(ProductStatusEnums.DISCONTINUED);
    ProductEntity deleted = productRepository.saveAndFlush(existing);
    
    // Invalidate caches when product is deleted
    redisCacheService.onProductDeleted(id);
    
    return deleted;
  }

  @Override
  public Page<ProductEntity> listProducts(final ListProductsDto criteria) {
    // Generate cache key for this specific query
    Map<String, Object> filters = new HashMap<>();
    if (criteria.getMinPriceAmount() != null) {
      filters.put("minPrice", criteria.getMinPriceAmount());
    }
    if (criteria.getMaxPriceAmount() != null) {
      filters.put("maxPrice", criteria.getMaxPriceAmount());
    }
    
    String cacheKey = redisCacheService.generateProductListCacheKey(
        criteria.getPage(), 
        criteria.getPageSize(), 
        criteria.getOnlyAvailable(), 
        filters
    );
    
    // Try cache first
    Optional<Page<ProductEntity>> cached = redisCacheService.getCachedProductList(cacheKey);
    if (cached.isPresent()) {
      return cached.get();
    }
    
    // Cache miss - query database
    final Pageable pageable = PageRequest.of(Math.max(0, criteria.getPage() - 1), Math.max(1, criteria.getPageSize()), Sort.by("id").descending());
    
    // Build dynamic specification for filtering
    Specification<ProductEntity> spec = Specification.where(null);
    
    // Filter by availability (only show active products by default)
    if (Boolean.TRUE.equals(criteria.getOnlyAvailable())) {
      spec = spec.and((root, query, cb) -> cb.greaterThan(root.get("stockQuantity"), 0L));
    }
    
    // Filter by price range
    if (criteria.getMinPriceAmount() != null) {
      final Double minPrice = criteria.getMinPriceAmount();
      spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("priceAmount"), minPrice));
    }
    
    if (criteria.getMaxPriceAmount() != null) {
      final Double maxPrice = criteria.getMaxPriceAmount();
      spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("priceAmount"), maxPrice));
    }
    
    // Always exclude discontinued products unless specifically requested
    spec = spec.and((root, query, cb) -> cb.notEqual(root.get("status"), ProductStatusEnums.DISCONTINUED));
    
    Page<ProductEntity> result = productRepository.findAll(spec, pageable);
    
    // Cache the result for future requests
    redisCacheService.cacheProductList(cacheKey, result);
    
    return result;
  }

  /* -------------------------
     Reservation / stock flows
     ------------------------- */

  @Override
  @Transactional
  public ReservationEntity reserveStock(final ReserveStockDto request) {
    reservationValidator.validateReserve(request);

    // Idempotency
    if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isEmpty()) {
      final Optional<ReservationEntity> existingReservation = reservationRepository.findByIdempotencyKey(request.getIdempotencyKey());
      if (existingReservation.isPresent()) {
        return existingReservation.get();
      }
    }

    final ProductEntity product = productRepository.findByIdForUpdate(request.getProductId())
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
    productRepository.save(product);

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

    ReservationEntity savedReservation = reservationRepository.save(reservation);
    
    // Invalidate list caches when stock changes (but not product metadata caches)
    redisCacheService.onStockModified(request.getProductId());
    
    return savedReservation;
  }

  @Override
  @Transactional
  public ReservationEntity confirmReservation(final ConfirmReservationDto request) {
    reservationValidator.validateConfirm(request);
    final ReservationEntity res = reservationRepository.findByReservationId(request.getReservationId())
        .orElseThrow(() -> new NotFoundException("reservation not found: " + request.getReservationId()));

    if ("CONFIRMED".equals(res.getStatus())) {
      return res;
    }
    if ("RELEASED".equals(res.getStatus())) {
      throw new IllegalStateException("reservation already released");
    }

    res.setStatus("CONFIRMED");
    if (request.getOrderId() != null) {
      res.setOrderId(request.getOrderId());
    }
    return reservationRepository.save(res);
  }

  @Override
  @Transactional
  public ReservationEntity releaseReservation(final ReleaseReservationDto request) {
    reservationValidator.validateRelease(request);
    final ReservationEntity res = reservationRepository.findByReservationId(request.getReservationId())
        .orElseThrow(() -> new NotFoundException("reservation not found: " + request.getReservationId()));

    if ("RELEASED".equals(res.getStatus())) {
      return res;
    }

    // Release the stock back to the product
    final ProductEntity product = productRepository.findByIdForUpdate(res.getProductId())
        .orElseThrow(() -> new NotFoundException("product not found: " + res.getProductId()));

    product.setStockQuantity(product.getStockQuantity() + res.getQuantity());
    productRepository.save(product);

    // Mark reservation as released
    res.setStatus("RELEASED");
    reservationRepository.save(res);
    
    // Invalidate list caches when stock changes (but not product metadata caches)
    redisCacheService.onStockModified(res.getProductId());
    
    return res;
  }
}

