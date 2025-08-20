package com.nuclei.product.dto;

import com.nuclei.product.entity.ProductEntity;
import com.nuclei.product.enums.ProductStatusEnums;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateProductDto {
  private String name;
  private String description;
  private Double priceAmount;
  private String priceCurrency;
  private Long stockQuantity;
  private ProductStatusEnums status;
  private Map<String, String> metadata;

  public ProductEntity toEntity() {
    final ProductEntity e = new ProductEntity();
    e.setName(name);
    e.setDescription(description);
    e.setPriceAmount(priceAmount == null ? 0.0 : priceAmount);
    e.setPriceCurrency(priceCurrency == null ? "INR" : priceCurrency);
    e.setStockQuantity(stockQuantity == null ? 0L : stockQuantity);
    e.setStatus(status == null ? ProductStatusEnums.ACTIVE : status);
    e.setMetadata(metadata);
    return e;
  }
}
