package com.nuclei.product.grpc;

import com.nuclei.product.dto.CreateProductDto;
import com.nuclei.product.dto.UpdateProductDto;
import com.nuclei.product.dto.ListProductsDto;
import com.nuclei.product.dto.ReserveStockDto;
import com.nuclei.product.dto.ConfirmReservationDto;
import com.nuclei.product.dto.ReleaseReservationDto;
import com.nuclei.product.entity.ProductEntity;
import com.nuclei.product.entity.ReservationEntity;
import com.nuclei.product.exception.NotFoundException;
import com.nuclei.product.mapper.ProductProtoMapper;
import com.nuclei.product.service.IProductService;
import com.nuclei.product.v1.messages.*;
import com.nuclei.product.v1.services.ProductServiceGrpc;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class ProductGrpcController extends ProductServiceGrpc.ProductServiceImplBase {

  private final IProductService productService;
  private final ProductProtoMapper mapper;

  public ProductGrpcController(
      final IProductService productService,
      final ProductProtoMapper mapper) {
    this.productService = productService;
    this.mapper = mapper;
  }

  /* ----------------- CRUD ----------------- */

  @Override
  public void createProduct(final CreateProductRequest req,
                            final StreamObserver<CreateProductResponse> responseObserver)
  {
    try {
      final CreateProductDto request = mapper.toCreateReq(req);
      final ProductEntity saved = productService.createProduct(request);
      responseObserver.onNext(mapper.toCreateProductResponse(saved));
      responseObserver.onCompleted();
    } catch (IllegalArgumentException e) {
      responseObserver.
          onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
    } catch (Exception e) {
      responseObserver.
          onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }
  }

  @Override
  public void getProduct(final GetProductRequest req,
                         final StreamObserver<GetProductResponse> responseObserver)
  {
    try {
      final long id = Long.parseLong(req.getId());
      final ProductEntity entity = productService.getProductById(id)
          .orElseThrow(() -> new NotFoundException("product not found"));
      responseObserver.onNext(mapper.toGetProductResponse(entity));
      responseObserver.onCompleted();
    } catch (NumberFormatException e) {
      responseObserver.
          onError(Status.INVALID_ARGUMENT.withDescription("invalid product id").asRuntimeException());
    } catch (NotFoundException nfe) {
      responseObserver.
          onError(Status.NOT_FOUND.withDescription(nfe.getMessage()).asRuntimeException());
    } catch (Exception e) {
      responseObserver.
          onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }
  }

  @Override
  public void updateProduct(final UpdateProductRequest req,
                            final StreamObserver<UpdateProductResponse> responseObserver)
  {
    try {
      final UpdateProductDto request = mapper.toUpdateReq(req);
      final ProductEntity updated = productService.updateProduct(request);
      responseObserver.onNext(mapper.toUpdateProductResponse(updated));
      responseObserver.onCompleted();
    } catch (IllegalArgumentException e) {
      responseObserver.
          onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
    } catch (NotFoundException nf) {
      responseObserver.
          onError(Status.NOT_FOUND.withDescription(nf.getMessage()).asRuntimeException());
    } catch (IllegalStateException ise) {
      responseObserver.
          onError(Status.ABORTED.withDescription(ise.getMessage()).asRuntimeException());
    } catch (Exception e) {
      responseObserver.
          onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }
  }

  @Override
  public void deleteProduct(final DeleteProductRequest req,
                            final StreamObserver<DeleteProductResponse> responseObserver)
  {
    try {
      final long id = Long.parseLong(req.getId());
      final ProductEntity deletedProduct = productService.deleteProduct(id);
      responseObserver.onNext(mapper.toDeleteProductResponse(deletedProduct));
      responseObserver.onCompleted();
    } catch (NumberFormatException e) {
      responseObserver.
          onError(Status.INVALID_ARGUMENT.withDescription("invalid id").asRuntimeException());
    } catch (NotFoundException nfe) {
      responseObserver.
          onError(Status.NOT_FOUND.withDescription(nfe.getMessage()).asRuntimeException());
    } catch (Exception e) {
      responseObserver.
          onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }
  }

  @Override
  public void listProducts(final ListProductsRequest req,
                           final StreamObserver<ListProductsResponse> responseObserver)
  {
    try {
      final ListProductsDto criteria = mapper.toListCriteria(req);
      final var page = productService.listProducts(criteria);
      responseObserver.onNext(mapper.toListProductsResponse(criteria, page));
      responseObserver.onCompleted();
    } catch (Exception e) {
      responseObserver.
          onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }
  }

  /* ----------------- Reservations ----------------- */

  @Override
  public void reserveStock(final ReserveStockRequest req,
                           final StreamObserver<ReserveStockResponse> responseObserver)
  {
    try {
      final ReserveStockDto request = mapper.toReserveReq(req);
      final ReservationEntity r = productService.reserveStock(request);
      responseObserver.onNext(mapper.toReserveResponse(r.getReservationId()));
      responseObserver.onCompleted();
    } catch (NumberFormatException e) {
      responseObserver.
          onError(Status.INVALID_ARGUMENT.withDescription("invalid product id").asRuntimeException());
    } catch (com.nuclei.product.exception.InsufficientStockException ie) {
      responseObserver.
          onError(Status.FAILED_PRECONDITION.withDescription(ie.getMessage()).asRuntimeException());
    } catch (IllegalStateException ise) {
      responseObserver.
          onError(Status.ABORTED.withDescription(ise.getMessage()).asRuntimeException());
    } catch (IllegalArgumentException iae) {
      responseObserver.
          onError(Status.INVALID_ARGUMENT.withDescription(iae.getMessage()).asRuntimeException());
    } catch (Exception e) {
      responseObserver.
          onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }
  }

  @Override
  public void confirmReservation(final ConfirmReservationRequest req,
                                 final StreamObserver<ConfirmReservationResponse> responseObserver)
  {
    try {
      final ConfirmReservationDto request = mapper.toConfirmReq(req);
      productService.confirmReservation(request);
      responseObserver.onNext(mapper.toConfirmResponse());
      responseObserver.onCompleted();
    } catch (NotFoundException nfe) {
      responseObserver.
          onError(Status.NOT_FOUND.withDescription(nfe.getMessage()).asRuntimeException());
    } catch (IllegalArgumentException iae) {
      responseObserver.
          onError(Status.INVALID_ARGUMENT.withDescription(iae.getMessage()).asRuntimeException());
    } catch (Exception e) {
      responseObserver.
          onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }
  }

  @Override
  public void releaseReservation(final ReleaseReservationRequest req,
                                 final StreamObserver<ReleaseReservationResponse> responseObserver)
  {
    try {
      final ReleaseReservationDto request = mapper.toReleaseReq(req);
      productService.releaseReservation(request);
      responseObserver.onNext(mapper.toReleaseResponse());
      responseObserver.onCompleted();
    } catch (NotFoundException nfe) {
      responseObserver.
          onError(Status.NOT_FOUND.withDescription(nfe.getMessage()).asRuntimeException());
    } catch (IllegalArgumentException iae) {
      responseObserver.
          onError(Status.INVALID_ARGUMENT.withDescription(iae.getMessage()).asRuntimeException());
    } catch (Exception e) {
      responseObserver
          .onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
    }
  }
}
