package com.nuclei.product.repository;

import com.nuclei.product.entity.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.LockModeType;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<ProductEntity, Long>, JpaSpecificationExecutor<ProductEntity> {

  // Find by ID with pessimistic locking for concurrent updates
  @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductEntity p WHERE p.id = :id")
  Optional<ProductEntity> findByIdWithLock(Long id);

  // Increment stock by quantity (used when releasing reservation)
  @Modifying
  @Query("UPDATE ProductEntity p SET p.stockQuantity = p.stockQuantity + :delta WHERE p.id = :id")
  int incrementStock(@Param("id") Long id, @Param("delta") Long delta);
}