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
import com.nuclei.product.validation.ProductValidator;
import com.nuclei.product.validation.ReservationValidator;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class ProductServiceImpl implements IProductService {

  private final ProductRepository productRepository;
  private final ReservationRepository reservationRepository;
  private final ProductValidator productValidator;
  private final ReservationValidator reservationValidator;

  public ProductServiceImpl(final ProductRepository productRepository,
                            final ReservationRepository reservationRepository,
                            final ProductValidator productValidator,
                            final ReservationValidator reservationValidator) {
    this.productRepository = productRepository;
    this.reservationRepository = reservationRepository;
    this.productValidator = productValidator;
    this.reservationValidator = reservationValidator;
  }

  @Override
  @Transactional
  public ProductEntity createProduct(final CreateProductDto request) {
    productValidator.validateCreate(request);
    final ProductEntity product = request.toEntity();
    if (product.getStockQuantity() == null) {
      product.setStockQuantity(0L);
    }
    return productRepository.saveAndFlush(product);
  }

  @Override
  public Optional<ProductEntity> getProductById(final Long id) {
    return productRepository.findById(id);
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

    return productRepository.saveAndFlush(existing);
  }

  @Override
  @Transactional
  public ProductEntity deleteProduct(final Long id) {
    final ProductEntity existing = productRepository.findById(id)
        .orElseThrow(() -> new NotFoundException("product not found: " + id));
    
    // Soft delete: mark as DISCONTINUED instead of hard delete
    existing.setStatus(ProductStatusEnums.DISCONTINUED);
    return productRepository.saveAndFlush(existing);
  }

  @Override
  public Page<ProductEntity> listProducts(final ListProductsDto criteria) {
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
    
    return productRepository.findAll(spec, pageable);
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

    final ProductEntity product = productRepository.findById(request.getProductId())
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
    } else {
      reservation.setTtlExpiresAt(Instant.now().plus(600, ChronoUnit.SECONDS));
    }

    reservationRepository.save(reservation);
    return reservation;
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

    final ProductEntity p = productRepository.findByIdForUpdate(res.getProductId())
        .orElseThrow(() -> new NotFoundException("product not found: " + res.getProductId()));

    p.setStockQuantity(p.getStockQuantity() + res.getQuantity());
    productRepository.save(p);

    res.setStatus("RELEASED");
    return reservationRepository.save(res);
  }
}
