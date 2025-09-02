package com.nuclei.product.service;

import com.nuclei.product.entity.ProductEntity;
import com.nuclei.product.exception.NotFoundException;
import com.nuclei.product.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductStockService {

  private final ProductRepository productRepository;

  public ProductStockService(final ProductRepository productRepository) {

    this.productRepository = productRepository;
  }

  /**
   * Increments stock (used to restore stock when reservation is released).
   */
  @Transactional
  public void releaseStock(final Long productId, final Long quantity) {
    final int updated = productRepository.incrementStock(productId, quantity);
    if (updated == 0) {
      throw new NotFoundException("product not found: " + productId);
    }
  }

  /**
   * Convenience read (if needed).
   */
  public ProductEntity getProduct(final Long productId) {
    return productRepository.findById(productId)
        .orElseThrow(() -> new NotFoundException("product not found: " + productId));
  }
}
