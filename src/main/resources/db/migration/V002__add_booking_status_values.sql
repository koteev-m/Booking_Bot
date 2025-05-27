-- Добавляем новые значения в ENUM booking_status
ALTER TYPE booking_status ADD VALUE 'COMPLETED';
ALTER TYPE booking_status ADD VALUE 'AWAITING_FEEDBACK';
ALTER TYPE booking_status ADD VALUE 'ARCHIVED';