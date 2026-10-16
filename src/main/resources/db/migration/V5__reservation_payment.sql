-- 4단계: 예매 → (PENDING) → 결제 승인 → CONFIRMED
alter table reservation
    add column payment_key varchar(100),
    add column paid_at     timestamp;
