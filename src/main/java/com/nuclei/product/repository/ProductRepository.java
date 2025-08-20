package com.nuclei.product.repository;

import com.nuclei.product.entity.ProductEntity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.LockModeType;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<ProductEntity, Long>, JpaSpecificationExecutor<ProductEntity> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from ProductEntity p where p.id = :id")
  Optional<ProductEntity> findByIdForUpdate(@Param("id") Long id);

  // Increment stock by quantity (used when releasing reservation)
  @Modifying
  @Query("update ProductEntity p set p.stockQuantity = p.stockQuantity + :delta where p.id = :id")
  int incrementStock(@Param("id") Long id, @Param("delta") Long delta);
}