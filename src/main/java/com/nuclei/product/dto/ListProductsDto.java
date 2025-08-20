package com.nuclei.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListProductsDto {

  @Builder.Default
  private Integer page = 1;

  @Builder.Default
  private Integer pageSize = 20;

  @Builder.Default
  private Boolean onlyAvailable = false;

  private Map<String, String> metadataFilter;
  private Double minPriceAmount;
  private Double maxPriceAmount;
}
