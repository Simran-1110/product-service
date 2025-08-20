package com.nuclei.product.exception;

public class InsufficientStockException extends RuntimeException {
  public InsufficientStockException(final String msg) {
    super(msg);
  }
}
