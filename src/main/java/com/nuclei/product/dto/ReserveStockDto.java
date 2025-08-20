package com.nuclei.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReserveStockDto {
  private Long productId;
  private long quantity;
  private String idempotencyKey;
  private Long expectedVersion;
  private Integer ttlSeconds;
}
