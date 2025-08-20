package com.nuclei.product.validation;

import com.nuclei.product.dto.CreateProductDto;
import com.nuclei.product.dto.UpdateProductDto;
import org.springframework.stereotype.Component;

@Component
public class ProductValidator {

  public void validateCreate(final CreateProductDto product) {
    if (product.getName() == null || product.getName().isBlank()) {
      throw new IllegalArgumentException("name is required");
    }
    if (product.getPriceAmount() == null) {
      throw new IllegalArgumentException("price.amount is required");
    }
    if (product.getPriceAmount() < 0) {
      throw new IllegalArgumentException("price.amount must be >= 0");
    }
    if (product.getPriceCurrency() == null || product.getPriceCurrency().isBlank()) {
      throw new IllegalArgumentException("price.currency is required");
    }
    if (product.getStockQuantity() == null) {
      throw new IllegalArgumentException("stock_quantity is required");
    }
    if (product.getStockQuantity() <= 0) {
      throw new IllegalArgumentException("stock_quantity must be > 0");
    }
  }

  public void validateUpdate(final UpdateProductDto product) {
    if (product.getId() == null) {
      throw new IllegalArgumentException("id is required");
    }
    if (product.getPriceAmount() != null && product.getPriceAmount() < 0) {
      throw new IllegalArgumentException("price.amount must be >= 0");
    }
    if (product.getStockQuantity() != null && product.getStockQuantity() < 0) {
      throw new IllegalArgumentException("stock_quantity must be >= 0");
    }
  }
}


