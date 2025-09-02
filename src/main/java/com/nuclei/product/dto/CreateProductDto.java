package com.nuclei.product.dto;

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
}
