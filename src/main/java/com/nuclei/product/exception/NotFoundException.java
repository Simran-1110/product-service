package com.nuclei.product.exception;

public class NotFoundException extends RuntimeException {
  public NotFoundException(final String msg) {
    super(msg);
  }
}
