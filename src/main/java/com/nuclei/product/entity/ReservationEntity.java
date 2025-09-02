package com.nuclei.product.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "reservations", uniqueConstraints = {
        @UniqueConstraint(name = "uc_reservation_idempotency", columnNames = {"idempotency_key"})
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationEntity extends Auditable {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "reservation_id", nullable = false, unique = true, length = 128)
  private String reservationId; // UUID string

  @Column(name = "product_id", nullable = false)
  private Long productId;

  @Column(nullable = false)
  private Long quantity;

  @Column(name = "status", nullable = false)
  private String status; // IN_PROGRESS, CONFIRMED, RELEASED

  @Column(name = "idempotency_key", length = 128)
  private String idempotencyKey; // optional unique id to avoid duplicate reservations

  @Column(name = "ttl_expires_at")
  private Instant ttlExpiresAt;

  @Column(name = "order_id")
  private String orderId;
}
