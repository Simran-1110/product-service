package com.nuclei.product.service;

import com.nuclei.product.dto.*;
import com.nuclei.product.entity.ProductEntity;
import com.nuclei.product.entity.ReservationEntity;
import com.nuclei.product.enums.ProductStatusEnums;
import com.nuclei.product.exception.InsufficientStockException;
import com.nuclei.product.exception.NotFoundException;
import com.nuclei.product.repository.ProductRepository;
import com.nuclei.product.repository.ReservationRepository;
import com.nuclei.product.service.impl.ProductServiceImpl;
import com.nuclei.product.service.IRedisCacheService;
import com.nuclei.product.validation.ProductValidator;
import com.nuclei.product.validation.ReservationValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProductServiceImplTest {

  @Mock
  private ProductRepository productRepository;
  @Mock
  private ReservationRepository reservationRepository;
  @Mock
  private ProductValidator productValidator;
  @Mock
  private ReservationValidator reservationValidator;
  @Mock
  private IRedisCacheService redisCacheService;

  private IProductService service;

  @BeforeEach
  void setUp() {
    service = new ProductServiceImpl(productRepository, reservationRepository, productValidator, reservationValidator, redisCacheService);
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

    ProductEntity saved = dto.toEntity();
    saved.setId(100L);

    doNothing().when(productValidator).validateCreate(dto);
    when(productRepository.saveAndFlush(any(ProductEntity.class))).thenReturn(saved);

    ProductEntity result = service.createProduct(dto);

    assertThat(result.getId()).isEqualTo(100L);
    verify(productValidator).validateCreate(dto);
    verify(productRepository).saveAndFlush(any(ProductEntity.class));
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

    when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
    when(productRepository.saveAndFlush(existing)).thenReturn(existing);

    ProductEntity updated = service.updateProduct(dto);

    assertThat(updated.getDescription()).isEqualTo("ND");
    assertThat(updated.getPriceAmount()).isEqualTo(25.0);
    assertThat(updated.getPriceCurrency()).isEqualTo("USD");
    assertThat(updated.getStockQuantity()).isEqualTo(7L);
    assertThat(updated.getStatus()).isEqualTo(ProductStatusEnums.INACTIVE);
  }

  @Test
  void updateProduct_versionMismatchThrows() {
    UpdateProductDto dto = UpdateProductDto.builder().id(1L).expectedVersion(3L).build();
    ProductEntity existing = newProduct(1L, 5L, 10.0, ProductStatusEnums.ACTIVE);
    existing.setVersion(2L);
    when(productRepository.findById(1L)).thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> service.updateProduct(dto))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("version mismatch");
  }

  @Test
  void deleteProduct_softDeletes() {
    ProductEntity existing = newProduct(2L, 5L, 10.0, ProductStatusEnums.ACTIVE);
    when(productRepository.findById(2L)).thenReturn(Optional.of(existing));
    when(productRepository.saveAndFlush(existing)).thenReturn(existing);

    ProductEntity deleted = service.deleteProduct(2L);
    assertThat(deleted.getStatus()).isEqualTo(ProductStatusEnums.DISCONTINUED);
  }

  @Test
  void deleteProduct_notFoundThrows() {
    when(productRepository.findById(99L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.deleteProduct(99L))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void listProducts_returnsPage() {
    ListProductsDto c = ListProductsDto.builder().page(1).pageSize(10).onlyAvailable(true).build();
    List<ProductEntity> items = List.of(newProduct(1L, 1L, 5.0, ProductStatusEnums.ACTIVE));
    Page<ProductEntity> page = new PageImpl<>(items, PageRequest.of(0, 10, Sort.by("id").descending()), 1);
    when(productRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class))).thenReturn(page);

    Page<ProductEntity> out = service.listProducts(c);
    assertThat(out.getTotalElements()).isEqualTo(1);
    verify(productRepository).findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class));
  }

  @Test
  void reserveStock_idempotentReturnsExisting() {
    ReserveStockDto dto = ReserveStockDto.builder().productId(1L).quantity(2L).idempotencyKey("abc").build();
    ReservationEntity existing = ReservationEntity.builder()
        .reservationId("r1").productId(1L).quantity(2L).status("IN_PROGRESS").build();
    doNothing().when(reservationValidator).validateReserve(dto);
    when(reservationRepository.findByIdempotencyKey("abc")).thenReturn(Optional.of(existing));

    ReservationEntity out = service.reserveStock(dto);
    assertThat(out.getReservationId()).isEqualTo("r1");
    verify(productRepository, never()).findByIdForUpdate(anyLong());
  }

  @Test
  void reserveStock_success_decrementsAndCreatesReservation() {
    ReserveStockDto dto = ReserveStockDto.builder().productId(4L).quantity(2L).idempotencyKey("abc").build();
    doNothing().when(reservationValidator).validateReserve(dto);

    ProductEntity product = newProduct(4L, 5L, 10.0, ProductStatusEnums.ACTIVE);
    product.setVersion(1L);
    when(productRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(product));

    ReservationEntity saved = ReservationEntity.builder().reservationId("r-new").productId(4L).quantity(2L).status("IN_PROGRESS").build();
    when(reservationRepository.save(any(ReservationEntity.class))).thenReturn(saved);

    ReservationEntity out = service.reserveStock(dto);
    assertThat(out.getReservationId()).isNotBlank();
    assertThat(product.getStockQuantity()).isEqualTo(3L);
    verify(productRepository).save(product);
  }

  @Test
  void reserveStock_discontinuedThrows() {
    ReserveStockDto dto = ReserveStockDto.builder().productId(1L).quantity(1L).build();
    doNothing().when(reservationValidator).validateReserve(dto);
    ProductEntity product = newProduct(1L, 5L, 10.0, ProductStatusEnums.DISCONTINUED);
    when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

    assertThatThrownBy(() -> service.reserveStock(dto))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("discontinued");
  }

  @Test
  void reserveStock_insufficientThrows() {
    ReserveStockDto dto = ReserveStockDto.builder().productId(1L).quantity(10L).build();
    doNothing().when(reservationValidator).validateReserve(dto);
    ProductEntity product = newProduct(1L, 5L, 10.0, ProductStatusEnums.ACTIVE);
    when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

    assertThatThrownBy(() -> service.reserveStock(dto))
        .isInstanceOf(InsufficientStockException.class);
  }

  @Test
  void reserveStock_versionMismatchThrows() {
    ReserveStockDto dto = ReserveStockDto.builder().productId(1L).quantity(1L).expectedVersion(5L).build();
    doNothing().when(reservationValidator).validateReserve(dto);
    ProductEntity product = newProduct(1L, 5L, 10.0, ProductStatusEnums.ACTIVE);
    product.setVersion(2L);
    when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

    assertThatThrownBy(() -> service.reserveStock(dto))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("version mismatch");
  }

  @Test
  void confirmReservation_setsConfirmed_andOrderId() {
    ConfirmReservationDto dto = new ConfirmReservationDto("rid-1", "order-1");
    ReservationEntity res = ReservationEntity.builder().reservationId("rid-1").status("IN_PROGRESS").build();
    doNothing().when(reservationValidator).validateConfirm(dto);
    when(reservationRepository.findByReservationId("rid-1")).thenReturn(Optional.of(res));
    when(reservationRepository.save(res)).thenReturn(res);

    ReservationEntity out = service.confirmReservation(dto);
    assertThat(out.getStatus()).isEqualTo("CONFIRMED");
    assertThat(out.getOrderId()).isEqualTo("order-1");
  }

  @Test
  void confirmReservation_alreadyReleasedThrows() {
    ConfirmReservationDto dto = new ConfirmReservationDto("rid-2", null);
    ReservationEntity res = ReservationEntity.builder().reservationId("rid-2").status("RELEASED").build();
    doNothing().when(reservationValidator).validateConfirm(dto);
    when(reservationRepository.findByReservationId("rid-2")).thenReturn(Optional.of(res));

    assertThatThrownBy(() -> service.confirmReservation(dto))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already released");
  }

  @Test
  void releaseReservation_restoresStock_andMarksReleased() {
    ReleaseReservationDto dto = new ReleaseReservationDto("rid-3", "reason");
    ReservationEntity res = ReservationEntity.builder().reservationId("rid-3").status("IN_PROGRESS").productId(9L).quantity(4L).build();
    doNothing().when(reservationValidator).validateRelease(dto);
    when(reservationRepository.findByReservationId("rid-3")).thenReturn(Optional.of(res));

    ProductEntity p = newProduct(9L, 3L, 2.0, ProductStatusEnums.ACTIVE);
    when(productRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(p));

    when(productRepository.save(p)).thenReturn(p);
    when(reservationRepository.save(res)).thenReturn(res);

    ReservationEntity out = service.releaseReservation(dto);
    assertThat(p.getStockQuantity()).isEqualTo(7L);
    assertThat(out.getStatus()).isEqualTo("RELEASED");
  }

  @Test
  void releaseReservation_alreadyReleased_noop() {
    ReleaseReservationDto dto = new ReleaseReservationDto("rid-x", null);
    ReservationEntity res = ReservationEntity.builder().reservationId("rid-x").status("RELEASED").build();
    doNothing().when(reservationValidator).validateRelease(dto);
    when(reservationRepository.findByReservationId("rid-x")).thenReturn(Optional.of(res));

    ReservationEntity out = service.releaseReservation(dto);
    assertThat(out.getStatus()).isEqualTo("RELEASED");
    verify(productRepository, never()).findByIdForUpdate(anyLong());
  }
}


