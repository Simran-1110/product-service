package com.nuclei.product.dao.impl;

import com.nuclei.product.dao.IReservationDao;
import com.nuclei.product.entity.ReservationEntity;
import com.nuclei.product.repository.ReservationRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Reservation DAO implementation
 */
@Repository
public class ReservationDaoImpl implements IReservationDao {
    
    private final ReservationRepository reservationRepository;
    
    public ReservationDaoImpl(final ReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
    }
    
    @Override
    public ReservationEntity save(final ReservationEntity reservation) {
        return reservationRepository.save(reservation);
    }
    
    @Override
    public Optional<ReservationEntity> findByReservationId(final String reservationId) {
        return reservationRepository.findByReservationId(reservationId);
    }
    
    @Override
    public Optional<ReservationEntity> findByIdempotencyKey(final String idempotencyKey) {
        return reservationRepository.findByIdempotencyKey(idempotencyKey);
    }
    
    @Override
    public List<ReservationEntity> findByStatusAndTtlExpiresAtLessThanEqual(final String status, final Instant now) {
        return reservationRepository.findByStatusAndTtlExpiresAtLessThanEqual(status, now);
    }
}
