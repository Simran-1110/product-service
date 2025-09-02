package com.nuclei.product.entity;

import com.nuclei.product.enums.ProductStatusEnums;
import com.nuclei.product.util.MapJsonConverter;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductEntity extends Auditable {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(length = 2000)
  private String description;

  @Column(name = "price_amount", nullable = false)
  private double priceAmount;

  @Column(name = "price_currency", nullable = false, length = 10)
  private String priceCurrency;

  @Column(name = "stock_quantity", nullable = false)
  private Long stockQuantity = 0L;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private ProductStatusEnums status = ProductStatusEnums.ACTIVE;

  @Version
  @Column(name = "version")
  private Long version;

  @Convert(converter = MapJsonConverter.class)
  @Column(columnDefinition = "TEXT")
  private Map<String, String> metadata;
}
