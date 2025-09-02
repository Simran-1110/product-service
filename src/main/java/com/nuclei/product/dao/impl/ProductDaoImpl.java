package com.nuclei.product.dao.impl;

import com.nuclei.product.dao.IProductDao;
import com.nuclei.product.entity.ProductEntity;
import com.nuclei.product.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Product DAO implementation
 */
@Repository
public class ProductDaoImpl implements IProductDao {
    
    private final ProductRepository productRepository;
    
    public ProductDaoImpl(final ProductRepository productRepository) {

        this.productRepository = productRepository;
    }
    
    @Override
    public ProductEntity save(final ProductEntity product) {

        return productRepository.save(product);
    }
    
    @Override
    public ProductEntity saveAndFlush(final ProductEntity product) {

        return productRepository.saveAndFlush(product);
    }
    
    @Override
    public Optional<ProductEntity> findById(final Long id) {

        return productRepository.findById(id);
    }
    
    @Override
    public Optional<ProductEntity> findByIdForUpdate(final Long id) {

        return productRepository.findByIdWithLock(id);
    }
    
    @Override
    public Page<ProductEntity> findAll(final Specification<ProductEntity> spec, final Pageable pageable) {
        return productRepository.findAll(spec, pageable);
    }
    
    @Override
    public int incrementStock(final Long id, final Long delta) {

        return productRepository.incrementStock(id, delta);
    }
}
