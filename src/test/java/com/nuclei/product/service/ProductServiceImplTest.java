package com.nuclei.product.service;

import com.nuclei.product.dao.IProductDao;
import com.nuclei.product.dao.IReservationDao;
import com.nuclei.product.dto.CreateProductDto;
import com.nuclei.product.dto.UpdateProductDto;
import com.nuclei.product.dto.ReserveStockDto;
import com.nuclei.product.dto.ConfirmReservationDto;
import com.nuclei.product.dto.ReleaseReservationDto;
import com.nuclei.product.entity.ProductEntity;
import com.nuclei.product.entity.ReservationEntity;
import com.nuclei.product.enums.ProductStatusEnums;
import com.nuclei.product.exception.InsufficientStockException;
import com.nuclei.product.exception.NotFoundException;
import com.nuclei.product.mapper.ProductProtoMapper;
import com.nuclei.product.service.impl.ProductServiceImpl;
import com.nuclei.product.util.ProductSpecificationBuilder;
import com.nuclei.product.validation.ProductValidator;
import com.nuclei.product.validation.ReservationValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

  @Mock
  private IProductDao productDao;

  @Mock
  private IReservationDao reservationDao;

  @Mock
  private ProductProtoMapper productMapper;

  @Mock
  private ProductValidator productValidator;

  @Mock
  private ReservationValidator reservationValidator;

  @Mock
  private IRedisCacheService redisCacheService;

  @Mock
  private ProductSpecificationBuilder specificationBuilder;

  private IProductService service;

  @BeforeEach
  void setUp() {
    service = new ProductServiceImpl(productDao, reservationDao, productMapper, productValidator, reservationValidator, redisCacheService, specificationBuilder);
  }

  private ProductEntity newProduct(Long id, long stock, double price, ProductStatusEnums status) {
    ProductEntity e = ProductEntity.builder()
        .id(id)
        .name("Test")
        .description("Desc")
        .priceAmount(price)
        .priceCurrency("INR")
        .stockQuantity(stock)
        .status(status)
        .version(1L)
        .build();
    return e;
  }

  @Test
  void createProduct_success() {
    CreateProductDto dto = CreateProductDto.builder()
        .name("P1")
        .description("D")
        .priceAmount(10.0)
        .priceCurrency("INR")
        .stockQuantity(5L)
        .status(ProductStatusEnums.ACTIVE)
        .build();

    ProductEntity product = newProduct(100L, 5L, 10.0, ProductStatusEnums.ACTIVE);

    doNothing().when(productValidator).validateCreate(dto);
    when(productMapper.toEntity(dto)).thenReturn(product);
    when(productDao.saveAndFlush(any(ProductEntity.class))).thenReturn(product);

    ProductEntity result = service.createProduct(dto);

    assertThat(result.getId()).isEqualTo(100L);
    verify(productValidator).validateCreate(dto);
    verify(productMapper).toEntity(dto);
    verify(productDao).saveAndFlush(any(ProductEntity.class));
    verify(redisCacheService).cacheProduct(eq(100L), eq(product));
  }

  @Test
  void updateProduct_successSelectiveFields_andVersionMatch() {
    UpdateProductDto dto = UpdateProductDto.builder()
        .id(1L)
        .description("ND")
        .priceAmount(25.0)
        .priceCurrency("USD")
        .stockQuantity(7L)
        .status(ProductStatusEnums.INACTIVE)
        .expectedVersion(2L)
        .build();

    ProductEntity existing = newProduct(1L, 5L, 10.0, ProductStatusEnums.ACTIVE);
    existing.setVersion(2L);
    
    ProductEntity updated = newProduct(1L, 7L, 25.0, ProductStatusEnums.INACTIVE);
    updated.setDescription("ND");
    updated.setPriceCurrency("USD");
    updated.setVersion(2L);

    when(productDao.findById(1L)).thenReturn(Optional.of(existing));
    when(productDao.saveAndFlush(existing)).thenReturn(updated);
    doNothing().when(productMapper).updateEntity(existing, dto);

    ProductEntity result = service.updateProduct(dto);

    assertThat(result.getDescription()).isEqualTo("ND");
    assertThat(result.getPriceAmount()).isEqualTo(25.0);
    assertThat(result.getPriceCurrency()).isEqualTo("USD");
    assertThat(result.getStockQuantity()).isEqualTo(7L);
    assertThat(result.getStatus()).isEqualTo(ProductStatusEnums.INACTIVE);
    verify(productMapper).updateEntity(existing, dto);
    verify(redisCacheService).onProductModified(1L);
  }

  @Test
  void updateProduct_versionMismatchThrows() {
    UpdateProductDto dto = UpdateProductDto.builder().id(1L).expectedVersion(3L).build();
    ProductEntity existing = newProduct(1L, 5L, 10.0, ProductStatusEnums.ACTIVE);
    existing.setVersion(2L);
    when(productDao.findById(1L)).thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> service.updateProduct(dto))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("version mismatch");
  }

  @Test
  void deleteProduct_softDeletes() {
    ProductEntity existing = newProduct(2L, 5L, 10.0, ProductStatusEnums.ACTIVE);
    when(productDao.findById(2L)).thenReturn(Optional.of(existing));
    when(productDao.saveAndFlush(existing)).thenReturn(existing);

    ProductEntity deleted = service.deleteProduct(2L);
    assertThat(deleted.getStatus()).isEqualTo(ProductStatusEnums.DISCONTINUED);
    verify(redisCacheService).onProductDeleted(2L);
  }

  @Test
  void deleteProduct_notFoundThrows() {
    when(productDao.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.deleteProduct(99L))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("product not found: 99");
  }

  @Test
  void getProductById_cacheHit() {
    ProductEntity cachedProduct = newProduct(1L, 5L, 10.0, ProductStatusEnums.ACTIVE);
    when(redisCacheService.getCachedProduct(1L)).thenReturn(cachedProduct);

    Optional<ProductEntity> result = service.getProductById(1L);

    assertThat(result).isPresent();
    assertThat(result.get().getId()).isEqualTo(1L);
    verify(productDao, never()).findById(anyLong());
  }

  @Test
  void getProductById_cacheMiss() {
    ProductEntity product = newProduct(1L, 5L, 10.0, ProductStatusEnums.ACTIVE);
    when(redisCacheService.getCachedProduct(1L)).thenReturn(null);
    when(productDao.findById(1L)).thenReturn(Optional.of(product));

    Optional<ProductEntity> result = service.getProductById(1L);

    assertThat(result).isPresent();
    assertThat(result.get().getId()).isEqualTo(1L);
    verify(redisCacheService).cacheProduct(1L, product);
  }

  @Test
  void reserveStock_success_decrementsAndCreatesReservation() {
    ReserveStockDto request = ReserveStockDto.builder()
        .productId(1L)
        .quantity(2L)
        .expectedVersion(1L)
        .build();

    ProductEntity product = newProduct(1L, 5L, 10.0, ProductStatusEnums.ACTIVE);
    product.setVersion(1L);

    when(productDao.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
    when(productDao.save(any(ProductEntity.class))).thenReturn(product);
    when(reservationDao.save(any(ReservationEntity.class))).thenAnswer(invocation -> {
      ReservationEntity reservation = invocation.getArgument(0);
      reservation.setId(1L);
      return reservation;
    });

    ReservationEntity result = service.reserveStock(request);

    assertThat(result).isNotNull();
    assertThat(result.getProductId()).isEqualTo(1L);
    assertThat(result.getQuantity()).isEqualTo(2L);
    assertThat(result.getStatus()).isEqualTo("IN_PROGRESS");
    verify(productDao).save(any(ProductEntity.class));
    verify(redisCacheService).onStockModified(1L);
  }

  @Test
  void reserveStock_insufficientStockThrows() {
    ReserveStockDto request = ReserveStockDto.builder()
        .productId(1L)
        .quantity(10L)
        .build();

    ProductEntity product = newProduct(1L, 5L, 10.0, ProductStatusEnums.ACTIVE);

    when(productDao.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

    assertThatThrownBy(() -> service.reserveStock(request))
        .isInstanceOf(InsufficientStockException.class)
        .hasMessageContaining("insufficient stock");
  }

  @Test
  void confirmReservation_success() {
    ConfirmReservationDto request = new ConfirmReservationDto("res-123", "order-456");

    ReservationEntity reservation = ReservationEntity.builder()
        .reservationId("res-123")
        .productId(1L)
        .quantity(2L)
        .status("IN_PROGRESS")
        .build();

    when(reservationDao.findByReservationId("res-123")).thenReturn(Optional.of(reservation));
    when(reservationDao.save(any(ReservationEntity.class))).thenReturn(reservation);

    ReservationEntity result = service.confirmReservation(request);

    assertThat(result.getStatus()).isEqualTo("CONFIRMED");
    assertThat(result.getOrderId()).isEqualTo("order-456");
  }

  @Test
  void releaseReservation_success() {
    ReleaseReservationDto request = new ReleaseReservationDto("res-123", "test reason");

    ReservationEntity reservation = ReservationEntity.builder()
        .reservationId("res-123")
        .productId(1L)
        .quantity(2L)
        .status("IN_PROGRESS")
        .build();

    ProductEntity product = newProduct(1L, 3L, 10.0, ProductStatusEnums.ACTIVE);

    when(reservationDao.findByReservationId("res-123")).thenReturn(Optional.of(reservation));
    when(productDao.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
    when(productDao.save(any(ProductEntity.class))).thenReturn(product);
    when(reservationDao.save(any(ReservationEntity.class))).thenReturn(reservation);

    ReservationEntity result = service.releaseReservation(request);

    assertThat(result.getStatus()).isEqualTo("RELEASED");
    verify(productDao).save(any(ProductEntity.class));
    verify(redisCacheService).onStockModified(1L);
  }
}


