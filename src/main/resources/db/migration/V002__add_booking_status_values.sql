-- Добавляем новые значения в ENUM booking_status, если они еще не существуют

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_enum WHERE enumlabel = 'COMPLETED' AND enumtypid = (SELECT oid FROM pg_type WHERE typname = 'booking_status')) THEN
        ALTER TYPE booking_status ADD VALUE 'COMPLETED';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_enum WHERE enumlabel = 'AWAITING_FEEDBACK' AND enumtypid = (SELECT oid FROM pg_type WHERE typname = 'booking_status')) THEN
        ALTER TYPE booking_status ADD VALUE 'AWAITING_FEEDBACK';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_enum WHERE enumlabel = 'ARCHIVED' AND enumtypid = (SELECT oid FROM pg_type WHERE typname = 'booking_status')) THEN
        ALTER TYPE booking_status ADD VALUE 'ARCHIVED';
    END IF;
END$$;