package com.nuclei.product.service;

import com.nuclei.product.entity.ReservationEntity;
import com.nuclei.product.repository.ReservationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
@Slf4j
public class ReservationSweeperService {

  private static final String IN_PROGRESS = "IN_PROGRESS";
  private static final String RELEASED = "RELEASED";

  private final ReservationRepository reservationRepository;
  private final ProductStockService productStockService;
  private final MeterRegistry meterRegistry;

  public ReservationSweeperService(final ReservationRepository reservationRepository,
                                   final ProductStockService productStockService,
                                   final MeterRegistry meterRegistry) {
    this.reservationRepository = reservationRepository;
    this.productStockService = productStockService;
    this.meterRegistry = meterRegistry;
  }

  /**
   * Sweeper job — scheduled via cron in application.yml (every 1 minute default).
   * It finds IN_PROGRESS reservations with ttlExipresAt <= now and releases them.
   */
  @Scheduled(cron = "${sweeper.cron}")
  public void sweepExpiredReservationsTask() {
    final Instant now = Instant.now();
    final List<ReservationEntity> expired = reservationRepository.findByStatusAndTtlExpiresAtLessThanEqual(IN_PROGRESS, now);
    if (expired.isEmpty()) {
      return;
    }
    log.info("Found {} expired reservations to release", expired.size());
    for (final ReservationEntity r : expired) {
      try {
        releaseReservation(r);
        meterRegistry.counter("reservations.released.total").increment();
        log.info("Released reservation {} (product={}, qty={})", r.getReservationId(), r.getProductId(), r.getQuantity());
      } catch (Exception ex) {
        meterRegistry.counter("reservations.release.failures").increment();
        log.error("Failed to release reservation {}: {}", r.getReservationId(), ex.getMessage(), ex);
      }
    }
  }

  @Transactional
  protected void releaseReservation(final ReservationEntity r) {
    // Double-check status to avoid races
    final ReservationEntity current = reservationRepository.findByReservationId(r.getReservationId())
        .orElseThrow(() -> new IllegalStateException("reservation not found: " + r.getReservationId()));

    if (!IN_PROGRESS.equals(current.getStatus())) {
      log.info("Skipping release for reservation {} with status {}", current.getReservationId(), current.getStatus());
      return;
    }

    // restore stock
    productStockService.releaseStock(current.getProductId(), current.getQuantity());

    // mark reservation released
    current.setStatus(RELEASED);
    reservationRepository.save(current);
  }
}
