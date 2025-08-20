CREATE TABLE products (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  price_amount DOUBLE NOT NULL,
  price_currency VARCHAR(10) NOT NULL,
  stock_quantity BIGINT NOT NULL DEFAULT 0,
  status VARCHAR(30) NOT NULL,
  version BIGINT,
  metadata TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE reservations (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  reservation_id VARCHAR(128) NOT NULL UNIQUE,
  product_id BIGINT NOT NULL,
  quantity BIGINT NOT NULL,
  status VARCHAR(30) NOT NULL,
  idempotency_key VARCHAR(128) UNIQUE,
  ttl_expires_at TIMESTAMP NULL,
  order_id VARCHAR(128),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_reservation_id (reservation_id),
  INDEX idx_idempotency (idempotency_key)
);
