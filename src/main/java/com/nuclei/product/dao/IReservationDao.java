package com.nuclei.product.dao;

import com.nuclei.product.entity.ReservationEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object interface for Reservation operations
 */
public interface IReservationDao {
    
    /**
     * Save a reservation entity
     */
    ReservationEntity save(final ReservationEntity reservation);
    
    /**
     * Find reservation by reservation ID
     */
    Optional<ReservationEntity> findByReservationId(final String reservationId);
    
    /**
     * Find reservation by idempotency key
     */
    Optional<ReservationEntity> findByIdempotencyKey(final String idempotencyKey);
    
    /**
     * Find expired reservations by status and TTL
     */
    List<ReservationEntity> findByStatusAndTtlExpiresAtLessThanEqual(final String status, final Instant now);
}
