package com.nuclei.product.validation;

import com.nuclei.product.dto.ConfirmReservationDto;
import com.nuclei.product.dto.ReleaseReservationDto;
import com.nuclei.product.dto.ReserveStockDto;
import org.springframework.stereotype.Component;

@Component
public class ReservationValidator {

  public void validateReserve(final ReserveStockDto command) {
    if (command.getProductId() == null) {
      throw new IllegalArgumentException("product_id is required");
    }
    if (command.getQuantity() <= 0) {
      throw new IllegalArgumentException("quantity must be > 0");
    }
    if (command.getTtlSeconds() != null && command.getTtlSeconds() < 0) {
      throw new IllegalArgumentException("ttl_seconds must be >= 0");
    }
  }

  public void validateConfirm(final ConfirmReservationDto command) {
    if (command.getReservationId() == null || command.getReservationId().isBlank()) {
      throw new IllegalArgumentException("reservation_id is required");
    }
  }

  public void validateRelease(final ReleaseReservationDto command) {
    if (command.getReservationId() == null || command.getReservationId().isBlank()) {
      throw new IllegalArgumentException("reservation_id is required");
    }
  }
}


