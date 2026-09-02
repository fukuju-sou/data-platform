CREATE DATABASE IF NOT EXISTS data_platform;

CREATE TABLE IF NOT EXISTS data_platform.card_transactions
(
    transaction_id UInt32,
    card_number String,
    purchase_date DateTime,
    category String,
    product String,
    unit_price UInt32,
    quantity UInt32,
    amount UInt32,
    customer_id String,
    name String,
    email String,
    address String
)
ENGINE = MergeTree()
ORDER BY (purchase_date, customer_id);
