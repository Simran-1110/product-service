package com.nuclei.product.repository;

import com.nuclei.product.entity.ReservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<ReservationEntity, Long> {

  Optional<ReservationEntity> findByReservationId(String reservationId);

  Optional<ReservationEntity> findByIdempotencyKey(String idempotencyKey);

  // Find reservations that are still IN_PROGRESS and expired by TTL
  List<ReservationEntity> findByStatusAndTtlExpiresAtLessThanEqual(String status, Instant now);

}
