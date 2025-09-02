package com.nuclei.product.mapper;

import com.nuclei.product.dto.*;
import com.nuclei.product.entity.ProductEntity;
import com.nuclei.product.enums.ProductStatusEnums;
import com.nuclei.product.v1.messages.*;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ProductProtoMapper {

  public CreateProductDto toCreateReq(final CreateProductRequest request) {
    final RequestProduct p = request.getProduct();
    final CreateProductDto cmd = new CreateProductDto();
    cmd.setName(p.getName());
    if (p.hasDescription()) {
      cmd.setDescription(p.getDescription());
    }
    if (p.hasPrice()) {
      cmd.setPriceAmount(p.getPrice().getAmount());
      cmd.setPriceCurrency(p.getPrice().getCurrency());
    }
    cmd.setStockQuantity(p.getStockQuantity());
    if (p.getStatus() != ProductStatus.DUMMY_PRODUCT_STATUS) {
      cmd.setStatus(ProductStatusEnums.valueOf(p.getStatus().name()));
    }
    if (!p.getMetadataMap().isEmpty()) {
      cmd.setMetadata(p.getMetadataMap());
    }
    return cmd;
  }

  public UpdateProductDto toUpdateReq(final UpdateProductRequest request) {
    final UpdateProductDto cmd = new UpdateProductDto();
    cmd.setId(Long.parseLong(request.getId()));
    if (request.hasDescription()) {
      cmd.setDescription(request.getDescription());
    }
    if (request.hasPrice()) {
      cmd.setPriceAmount(request.getPrice().getAmount());
      cmd.setPriceCurrency(request.getPrice().getCurrency());
    }
    if (request.hasStockQuantity()) {
      cmd.setStockQuantity(request.getStockQuantity());
    }
    if (request.hasStatus() && request.getStatus() != ProductStatus.DUMMY_PRODUCT_STATUS) {
      cmd.setStatus(ProductStatusEnums.valueOf(request.getStatus().name()));
    }
    if (request.hasExpectedVersion()) {
      cmd.setExpectedVersion(request.getExpectedVersion());
    }
    return cmd;
  }

  public ListProductsDto toListCriteria(final ListProductsRequest req) {
    final ListProductsDto c = new ListProductsDto();
    c.setOnlyAvailable(req.hasOnlyAvailable() && req.getOnlyAvailable());
    c.setPage(req.getPage() == 0 ? 1 : req.getPage());
    c.setPageSize(req.getPageSize() == 0 ? 20 : req.getPageSize());
    if (req.hasMinPrice()) {
      c.setMinPriceAmount(req.getMinPrice().getAmount());
    }
    if (req.hasMaxPrice()) {
      c.setMaxPriceAmount(req.getMaxPrice().getAmount());
    }
    return c;
  }

  public ReserveStockDto toReserveReq(final ReserveStockRequest req) {
    final ReserveStockDto c = new ReserveStockDto();
    c.setProductId(Long.parseLong(req.getProductId()));
    c.setQuantity(req.getQuantity());
    if (req.hasIdempotencyKey()) {
      c.setIdempotencyKey(req.getIdempotencyKey());
    }
    if (req.hasExpectedVersion()) {
      c.setExpectedVersion(req.getExpectedVersion());
    }
    if (req.hasTtlSeconds()) {
      c.setTtlSeconds(req.getTtlSeconds());
    }
    return c;
  }

  public ConfirmReservationDto toConfirmReq(final ConfirmReservationRequest req) {
    final ConfirmReservationDto c = new ConfirmReservationDto();
    c.setReservationId(req.getReservationId());
    if (req.hasOrderId()) {
      c.setOrderId(req.getOrderId());
    }
    return c;
  }

  public ReleaseReservationDto toReleaseReq(final ReleaseReservationRequest req) {
    final ReleaseReservationDto c = new ReleaseReservationDto();
    c.setReservationId(req.getReservationId());
    if (req.hasReason()) {
      c.setReason(req.getReason());
    }
    return c;
  }

  public Product toProto(final ProductEntity e) {
    final Product.Builder b = Product.newBuilder()
        .setId(e.getId() != null ? String.valueOf(e.getId()) : "")
        .setName(Optional.ofNullable(e.getName()).orElse(""))
        .setPrice(
            Money.newBuilder()
                .setAmount(e.getPriceAmount())
                .setCurrency(Optional.ofNullable(e.getPriceCurrency()).orElse(""))
                .build()
        )
        .setStockQuantity(e.getStockQuantity() == null ? 0L : e.getStockQuantity())
        .setVersion(e.getVersion() == null ? 0L : e.getVersion())
        .setCreatedAt(e.getCreatedAt() == null ? "" : e.getCreatedAt().toString())
        .setUpdatedAt(e.getUpdatedAt() == null ? "" : e.getUpdatedAt().toString());

    if (e.getStatus() != null) {
      try {
        b.setStatus(ProductStatus.valueOf(e.getStatus().name()));
      } catch (IllegalArgumentException ex) {
        b.setStatus(ProductStatus.DUMMY_PRODUCT_STATUS);
      }
    } else {
      b.setStatus(ProductStatus.DUMMY_PRODUCT_STATUS);
    }

    if (e.getDescription() != null) {
      b.setDescription(e.getDescription());
    }
    if (e.getMetadata() != null && !e.getMetadata().isEmpty()) {
      b.putAllMetadata(e.getMetadata());
    }
    return b.build();
  }

  public ReserveStockResponse toReserveResponse(final String reservationId) {
    final ReserveStockResponse.Builder b = ReserveStockResponse.newBuilder().setSuccess(true);
    if (reservationId != null) {
      b.setReservationId(reservationId);
    }
    return b.build();
  }

  public ConfirmReservationResponse toConfirmResponse() {
    return ConfirmReservationResponse.newBuilder().setSuccess(true).build();
  }

  public ReleaseReservationResponse toReleaseResponse() {
    return ReleaseReservationResponse.newBuilder().setSuccess(true).build();
  }

  public CreateProductResponse toCreateProductResponse(final ProductEntity product) {
    return CreateProductResponse.newBuilder()
        .setProduct(toProto(product))
        .build();
  }

  public GetProductResponse toGetProductResponse(final ProductEntity product) {
    return GetProductResponse.newBuilder()
        .setProduct(toProto(product))
        .build();
  }

  public UpdateProductResponse toUpdateProductResponse(final ProductEntity product) {
    return UpdateProductResponse.newBuilder()
        .setProduct(toProto(product))
        .build();
  }

  public DeleteProductResponse toDeleteProductResponse(final ProductEntity product) {
    return DeleteProductResponse.newBuilder()
        .setSuccess(true)
        .setProduct(toProto(product))
        .build();
  }

  public ListProductsResponse toListProductsResponse(final ListProductsDto criteria, final Page<ProductEntity> page) {
    final ListProductsResponse.Builder rb = ListProductsResponse.newBuilder();
    page.getContent().forEach(prod -> rb.addProducts(toProto(prod)));
    rb.setPage(criteria.getPage())
        .setPageSize(criteria.getPageSize())
        .setTotal((int) page.getTotalElements());
    return rb.build();
  }

  /**
   * Convert CreateProductDto to ProductEntity
   */
  public ProductEntity toEntity(final CreateProductDto request) {
    final ProductEntity entity = new ProductEntity();
    entity.setName(request.getName());
    entity.setDescription(request.getDescription());
    entity.setPriceAmount(request.getPriceAmount() == null ? 0.0 : request.getPriceAmount());
    entity.setPriceCurrency(request.getPriceCurrency() == null ? "INR" : request.getPriceCurrency());
    entity.setStockQuantity(request.getStockQuantity() == null ? 0L : request.getStockQuantity());
    entity.setStatus(request.getStatus() == null ? ProductStatusEnums.ACTIVE : request.getStatus());
    entity.setMetadata(request.getMetadata());
    return entity;
  }

  /**
   * Update existing ProductEntity with UpdateProductDto
   */
  public void updateEntity(final ProductEntity entity, final UpdateProductDto request) {
    if (request.getName() != null) {
      entity.setName(request.getName());
    }
    if (request.getDescription() != null) {
      entity.setDescription(request.getDescription());
    }
    if (request.getPriceAmount() != null) {
      entity.setPriceAmount(request.getPriceAmount());
    }
    if (request.getPriceCurrency() != null) {
      entity.setPriceCurrency(request.getPriceCurrency());
    }
    if (request.getStockQuantity() != null) {
      entity.setStockQuantity(request.getStockQuantity());
    }
    if (request.getStatus() != null) {
      entity.setStatus(request.getStatus());
    }
  }
}
