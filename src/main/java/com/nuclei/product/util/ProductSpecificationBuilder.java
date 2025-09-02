package com.nuclei.product.util;

import com.nuclei.product.dto.ListProductsDto;
import com.nuclei.product.entity.ProductEntity;
import com.nuclei.product.enums.ProductStatusEnums;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import javax.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for building dynamic product specifications based on filter criteria
 */
@Component
public class ProductSpecificationBuilder {
    
    /**
     * Build specification for product listing with filters
     */
    public Specification<ProductEntity> buildSpecification(final ListProductsDto criteria) {
        return (root, query, cb) -> {
            final List<Predicate> predicates = new ArrayList<>();
            
            // Filter by availability (only show active products by default)
            if (Boolean.TRUE.equals(criteria.getOnlyAvailable())) {
                predicates.add(cb.greaterThan(root.get("stockQuantity"), 0L));
            }
            
            // Filter by price range
            if (criteria.getMinPriceAmount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("priceAmount"), criteria.getMinPriceAmount()));
            }
            
            if (criteria.getMaxPriceAmount() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("priceAmount"), criteria.getMaxPriceAmount()));
            }
            
            // Always exclude discontinued products unless specifically requested
            predicates.add(cb.notEqual(root.get("status"), ProductStatusEnums.DISCONTINUED));
            
            // Ensure we only get products with valid timestamps to prevent caching issues
            predicates.add(cb.isNotNull(root.get("createdAt")));
            predicates.add(cb.isNotNull(root.get("updatedAt")));
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
