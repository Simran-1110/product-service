package com.nuclei.product.service;

import com.nuclei.product.dto.CreateProductDto;
import com.nuclei.product.dto.UpdateProductDto;
import com.nuclei.product.dto.ListProductsDto;
import com.nuclei.product.dto.ReserveStockDto;
import com.nuclei.product.dto.ConfirmReservationDto;
import com.nuclei.product.dto.ReleaseReservationDto;
import com.nuclei.product.entity.ProductEntity;
import com.nuclei.product.entity.ReservationEntity;
import org.springframework.data.domain.Page;

import java.util.Optional;

public interface IProductService {

  ProductEntity createProduct(CreateProductDto request);

  Optional<ProductEntity> getProductById(Long id);

  ProductEntity updateProduct(UpdateProductDto request);

  ProductEntity deleteProduct(Long id);

  Page<ProductEntity> listProducts(ListProductsDto criteria);

  ReservationEntity reserveStock(ReserveStockDto request);

  ReservationEntity confirmReservation(ConfirmReservationDto request);

  ReservationEntity releaseReservation(ReleaseReservationDto request);
}
