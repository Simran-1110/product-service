package com.nuclei.product.service;

import com.nuclei.product.dto.*;
import com.nuclei.product.entity.ProductEntity;
import com.nuclei.product.entity.ReservationEntity;
import org.springframework.data.domain.Page;

import java.util.Optional;

public interface IProductService {

  ProductEntity createProduct(CreateProductDto command);

  Optional<ProductEntity> getProductById(Long id);

  ProductEntity updateProduct(UpdateProductDto command);

  ProductEntity deleteProduct(Long id);

  Page<ProductEntity> listProducts(ListProductsDto criteria);

  ReservationEntity reserveStock(ReserveStockDto command);

  ReservationEntity confirmReservation(ConfirmReservationDto command);

  ReservationEntity releaseReservation(ReleaseReservationDto command);
}
