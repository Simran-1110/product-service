package com.nuclei.product.grpc;

import com.nuclei.product.dto.*;
import com.nuclei.product.entity.ProductEntity;
import com.nuclei.product.entity.ReservationEntity;
import com.nuclei.product.exception.NotFoundException;
import com.nuclei.product.mapper.ProductProtoMapper;
import com.nuclei.product.service.IProductService;
import com.nuclei.product.v1.messages.*;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProductGrpcControllerTest {

  @Mock
  private IProductService productService;
  @Mock
  private ProductProtoMapper mapper;

  private ProductGrpcController controller;

  @BeforeEach
  void setUp() {
    controller = new ProductGrpcController(productService, mapper);
  }

  private static class TestObserver<T> implements StreamObserver<T> {
    T next;
    Throwable error;
    boolean completed;
    @Override public void onNext(T value) { this.next = value; }
    @Override public void onError(Throwable t) { this.error = t; }
    @Override public void onCompleted() { this.completed = true; }
  }

  @Test
  void createProduct_success() {
    CreateProductRequest req = CreateProductRequest.newBuilder()
        .setProduct(RequestProduct.newBuilder().setName("a").setPrice(Money.newBuilder().setAmount(1).setCurrency("INR").build()).setStockQuantity(1).build())
        .build();
    CreateProductDto cmd = new CreateProductDto();
    when(mapper.toCreateCommand(req)).thenReturn(cmd);
    ProductEntity saved = new ProductEntity();
    when(productService.createProduct(cmd)).thenReturn(saved);
    Product proto = Product.newBuilder().setId("1").build();
    when(mapper.toProto(saved)).thenReturn(proto);

    TestObserver<CreateProductResponse> obs = new TestObserver<>();
    controller.createProduct(req, obs);

    assertThat(obs.error).isNull();
    assertThat(obs.completed).isTrue();
    assertThat(obs.next.getProduct()).isEqualTo(proto);
  }

  @Test
  void getProduct_invalidId_mapsInvalidArgument() {
    GetProductRequest req = GetProductRequest.newBuilder().setId("abc").build();
    TestObserver<GetProductResponse> obs = new TestObserver<>();
    controller.getProduct(req, obs);
    assertThat(obs.error).isInstanceOf(StatusRuntimeException.class);
    StatusRuntimeException e = (StatusRuntimeException) obs.error;
    assertThat(e.getStatus().getCode().name()).isEqualTo("INVALID_ARGUMENT");
  }

  @Test
  void getProduct_notFound_mapsNotFound() {
    GetProductRequest req = GetProductRequest.newBuilder().setId("10").build();
    when(productService.getProductById(10L)).thenReturn(Optional.empty());
    TestObserver<GetProductResponse> obs = new TestObserver<>();
    controller.getProduct(req, obs);
    assertThat(obs.error).isInstanceOf(StatusRuntimeException.class);
    assertThat(((StatusRuntimeException) obs.error).getStatus().getCode().name()).isEqualTo("NOT_FOUND");
  }

  @Test
  void updateProduct_versionMismatch_mapsAborted() {
    UpdateProductRequest req = UpdateProductRequest.newBuilder().setId("1").build();
    UpdateProductDto cmd = new UpdateProductDto();
    when(mapper.toUpdateCommand(req)).thenReturn(cmd);
    when(productService.updateProduct(cmd)).thenThrow(new IllegalStateException("version mismatch"));
    TestObserver<UpdateProductResponse> obs = new TestObserver<>();
    controller.updateProduct(req, obs);
    assertThat(obs.error).isInstanceOf(StatusRuntimeException.class);
    assertThat(((StatusRuntimeException) obs.error).getStatus().getCode().name()).isEqualTo("ABORTED");
  }

  @Test
  void deleteProduct_invalidId_mapsInvalidArgument() {
    DeleteProductRequest req = DeleteProductRequest.newBuilder().setId("x").build();
    TestObserver<DeleteProductResponse> obs = new TestObserver<>();
    controller.deleteProduct(req, obs);
    assertThat(obs.error).isInstanceOf(StatusRuntimeException.class);
    assertThat(((StatusRuntimeException) obs.error).getStatus().getCode().name()).isEqualTo("INVALID_ARGUMENT");
  }

  @Test
  void listProducts_success() {
    ListProductsRequest req = ListProductsRequest.newBuilder().setPage(1).setPageSize(2).build();
    ListProductsDto criteria = new ListProductsDto();
    when(mapper.toListCriteria(req)).thenReturn(criteria);
    when(productService.listProducts(criteria)).thenReturn(new PageImpl<>(List.of()))
        ;
    TestObserver<ListProductsResponse> obs = new TestObserver<>();
    controller.listProducts(req, obs);
    assertThat(obs.error).isNull();
    assertThat(obs.completed).isTrue();
    assertThat(obs.next.getPage()).isEqualTo(1);
  }

  @Test
  void reserveStock_insufficient_mapsFailedPrecondition() {
    ReserveStockRequest req = ReserveStockRequest.newBuilder().setProductId("1").setQuantity(5).build();
    ReserveStockDto cmd = new ReserveStockDto();
    when(mapper.toReserveCommand(req)).thenReturn(cmd);
    when(productService.reserveStock(cmd)).thenThrow(new com.nuclei.product.exception.InsufficientStockException("nope"));
    TestObserver<ReserveStockResponse> obs = new TestObserver<>();
    controller.reserveStock(req, obs);
    assertThat(obs.error).isInstanceOf(StatusRuntimeException.class);
    assertThat(((StatusRuntimeException) obs.error).getStatus().getCode().name()).isEqualTo("FAILED_PRECONDITION");
  }

  @Test
  void reserveStock_illegalState_mapsAborted() {
    ReserveStockRequest req = ReserveStockRequest.newBuilder().setProductId("1").setQuantity(5).build();
    ReserveStockDto cmd = new ReserveStockDto();
    when(mapper.toReserveCommand(req)).thenReturn(cmd);
    when(productService.reserveStock(cmd)).thenThrow(new IllegalStateException("version"));
    TestObserver<ReserveStockResponse> obs = new TestObserver<>();
    controller.reserveStock(req, obs);
    assertThat(obs.error).isInstanceOf(StatusRuntimeException.class);
    assertThat(((StatusRuntimeException) obs.error).getStatus().getCode().name()).isEqualTo("ABORTED");
  }

  @Test
  void reserveStock_success() {
    ReserveStockRequest req = ReserveStockRequest.newBuilder().setProductId("1").setQuantity(2).build();
    ReserveStockDto cmd = new ReserveStockDto();
    when(mapper.toReserveCommand(req)).thenReturn(cmd);
    ReservationEntity res = ReservationEntity.builder().reservationId("rid-123").build();
    when(productService.reserveStock(cmd)).thenReturn(res);
    when(mapper.toReserveResponse("rid-123")).thenReturn(ReserveStockResponse.newBuilder().setSuccess(true).setReservationId("rid-123").build());
    TestObserver<ReserveStockResponse> obs = new TestObserver<>();
    controller.reserveStock(req, obs);
    assertThat(obs.error).isNull();
    assertThat(obs.completed).isTrue();
    assertThat(obs.next.getReservationId()).isEqualTo("rid-123");
  }

  @Test
  void confirmReservation_notFound_mapsNotFound() {
    ConfirmReservationRequest req = ConfirmReservationRequest.newBuilder().setReservationId("r1").build();
    ConfirmReservationDto cmd = new ConfirmReservationDto();
    when(mapper.toConfirmCommand(req)).thenReturn(cmd);
    when(productService.confirmReservation(cmd)).thenThrow(new NotFoundException("missing"));
    TestObserver<ConfirmReservationResponse> obs = new TestObserver<>();
    controller.confirmReservation(req, obs);
    assertThat(obs.error).isInstanceOf(StatusRuntimeException.class);
    assertThat(((StatusRuntimeException) obs.error).getStatus().getCode().name()).isEqualTo("NOT_FOUND");
  }

  @Test
  void releaseReservation_notFound_mapsNotFound() {
    ReleaseReservationRequest req = ReleaseReservationRequest.newBuilder().setReservationId("r1").build();
    ReleaseReservationDto cmd = new ReleaseReservationDto();
    when(mapper.toReleaseCommand(req)).thenReturn(cmd);
    when(productService.releaseReservation(cmd)).thenThrow(new NotFoundException("missing"));
    TestObserver<ReleaseReservationResponse> obs = new TestObserver<>();
    controller.releaseReservation(req, obs);
    assertThat(obs.error).isInstanceOf(StatusRuntimeException.class);
    assertThat(((StatusRuntimeException) obs.error).getStatus().getCode().name()).isEqualTo("NOT_FOUND");
  }

  @Test
  void confirmReservation_success() {
    ConfirmReservationRequest req = ConfirmReservationRequest.newBuilder().setReservationId("r1").build();
    ConfirmReservationDto cmd = new ConfirmReservationDto();
    when(mapper.toConfirmCommand(req)).thenReturn(cmd);
    doReturn(ConfirmReservationResponse.newBuilder().setSuccess(true).build()).when(mapper).toConfirmResponse();
    TestObserver<ConfirmReservationResponse> obs = new TestObserver<>();
    controller.confirmReservation(req, obs);
    assertThat(obs.error).isNull();
    assertThat(obs.completed).isTrue();
    assertThat(obs.next.getSuccess()).isTrue();
  }

  @Test
  void releaseReservation_success() {
    ReleaseReservationRequest req = ReleaseReservationRequest.newBuilder().setReservationId("r1").build();
    ReleaseReservationDto cmd = new ReleaseReservationDto();
    when(mapper.toReleaseCommand(req)).thenReturn(cmd);
    doReturn(ReleaseReservationResponse.newBuilder().setSuccess(true).build()).when(mapper).toReleaseResponse();
    TestObserver<ReleaseReservationResponse> obs = new TestObserver<>();
    controller.releaseReservation(req, obs);
    assertThat(obs.error).isNull();
    assertThat(obs.completed).isTrue();
    assertThat(obs.next.getSuccess()).isTrue();
  }
}


