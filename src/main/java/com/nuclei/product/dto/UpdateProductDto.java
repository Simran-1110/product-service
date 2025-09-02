package com.nuclei.product.dto;

import com.nuclei.product.enums.ProductStatusEnums;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProductDto {
  private Long id;
  private String name;
  private String description;
  private Double priceAmount;
  private String priceCurrency;
  private Long stockQuantity;
  private ProductStatusEnums status;
  private Long expectedVersion;
}
