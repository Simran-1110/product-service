package com.nuclei.product.validation;

import com.nuclei.product.dto.CreateProductDto;
import com.nuclei.product.dto.UpdateProductDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ProductValidatorTest {

  private final ProductValidator validator = new ProductValidator();

  @Test
  void validateCreate_ok() {
    CreateProductDto dto = CreateProductDto.builder()
        .name("X")
        .priceAmount(1.0)
        .priceCurrency("INR")
        .stockQuantity(1L)
        .build();
    validator.validateCreate(dto);
  }

  @Test
  void validateCreate_missingName_throws() {
    CreateProductDto dto = CreateProductDto.builder()
        .priceAmount(1.0)
        .priceCurrency("INR")
        .stockQuantity(1L)
        .build();
    assertThatThrownBy(() -> validator.validateCreate(dto))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name is required");
  }

  @Test
  void validateUpdate_negativePrice_fails() {
    UpdateProductDto dto = UpdateProductDto.builder().id(1L).priceAmount(-1.0).build();
    assertThatThrownBy(() -> validator.validateUpdate(dto))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("price.amount must be >= 0");
  }
}


