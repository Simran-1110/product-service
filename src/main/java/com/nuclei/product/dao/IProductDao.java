package com.nuclei.product.dao;

import com.nuclei.product.entity.ProductEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.Optional;

/**
 * Data Access Object interface for Product operations
 */
public interface IProductDao {
    
    /**
     * Save a product entity
     */
    ProductEntity save(final ProductEntity product);
    
    /**
     * Save and flush a product entity
     */
    ProductEntity saveAndFlush(final ProductEntity product);
    
    /**
     * Find product by ID
     */
    Optional<ProductEntity> findById(final Long id);
    
    /**
     * Find product by ID with pessimistic lock for update
     */
    Optional<ProductEntity> findByIdForUpdate(final Long id);
    
    /**
     * Find all products with specification and pagination
     */
    Page<ProductEntity> findAll(final Specification<ProductEntity> spec, final Pageable pageable);
    
    /**
     * Increment stock by delta amount
     */
    int incrementStock(final Long id, final Long delta);
}
