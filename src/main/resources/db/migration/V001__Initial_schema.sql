/* ==========================================================
   V001__Initial_schema.sql
   Схема: clubs / tables / users / bookings   (PostgreSQL 16)
   ========================================================== */

-- 1. Справочник клубов -------------------------------------

CREATE TABLE clubs (
    id                  serial PRIMARY KEY,
    code                text        NOT NULL UNIQUE,          -- MACHINE name: MIX / OSOBNYAK …
    title               text        NOT NULL,                 -- Читаемое имя
    description         text,                                 -- Описание клуба (опционально)
    address             text,                                 -- Адрес клуба (опционально)
    phone               text,                                 -- Телефон клуба (опционально)
    working_hours       text,                                 -- Время работы (опционально)
    timezone            text        NOT NULL DEFAULT 'Europe/Moscow',
    photo_url           text,                                 -- Ссылка на фото клуба (опц.)
    floor_plan_image_url text,                                -- Ссылка на схему столов (опц.)
    is_active           boolean     NOT NULL DEFAULT true,
    created_at          timestamptz NOT NULL DEFAULT now()
);

-- Ускоряем поиск клуба по code
CREATE UNIQUE INDEX IF NOT EXISTS clubs_code_idx ON clubs (code);

-- 2. Справочник столов -------------------------------------

CREATE TABLE tables (
    id                  serial PRIMARY KEY,
    club_id             int         NOT NULL REFERENCES clubs(id) ON DELETE CASCADE,
    number              int         NOT NULL,                 -- Номер стола (1, 2, 3…)
    seats               int         NOT NULL,                 -- Кол-во мест
    description         text,                                 -- Описание стола (опц.)
    pos_x               integer,                              -- Для визуализации на схеме (опц.)
    pos_y               integer,                              -- Для визуализации на схеме (опц.)
    photo_url           text,                                 -- Ссылка на схему-фото (опц.)
    is_active           boolean     NOT NULL DEFAULT true,
    UNIQUE (club_id, number)                                  -- номер уникален внутри клуба
);

CREATE INDEX IF NOT EXISTS tables_club_idx ON tables (club_id);

-- 3. Пользователи Telegram ----------------------------------

CREATE TABLE users (
    id                  serial PRIMARY KEY,
    telegram_id         bigint      NOT NULL UNIQUE,
    first_name          text,
    last_name           text,
    username            text,
    phone               text,
    language_code       varchar(5)  NOT NULL DEFAULT 'ru',
    loyalty_points      integer     NOT NULL DEFAULT 0,
    created_at          timestamptz NOT NULL DEFAULT now(),
    last_activity_at    timestamptz NOT NULL DEFAULT now()
);

-- 4. Таблица броней ----------------------------------------

CREATE TYPE booking_status AS ENUM ('NEW', 'CONFIRMED', 'CANCELLED');

CREATE TABLE bookings (
    id                  serial PRIMARY KEY,
    club_id             int         NOT NULL REFERENCES clubs(id)    ON DELETE CASCADE,
    table_id            int         NOT NULL REFERENCES tables(id)   ON DELETE CASCADE,
    user_id             int         NOT NULL REFERENCES users(id)    ON DELETE SET NULL, -- Consider if user deletion should cascade or set null
    guests_count        int         NOT NULL CHECK (guests_count > 0),
    date_start          timestamptz NOT NULL,        -- начало интервала
    date_end            timestamptz NOT NULL,        -- конец интервала
    status              booking_status NOT NULL DEFAULT 'NEW',
    comment             text,
    guest_name          text,
    guest_phone         text,
    loyalty_points_earned integer,
    feedback_rating     integer,
    feedback_comment    text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz
);

-- Часто используемые выборки -------------------------------

-- ➊ Быстрый поиск броней «на сегодня» по клубу
CREATE INDEX IF NOT EXISTS bookings_today_idx
    ON bookings (club_id, date_start)
    WHERE (date_start >= date_trunc('day', now())
      AND  date_start <  date_trunc('day', now() + interval '1 day'));

-- ➋ Уникальность: в один интервал один стол бронируем 1 раз
-- Для оператора EXCLUDE нужен GiST-индекс:
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE bookings
    ADD CONSTRAINT bookings_no_overlaps
    EXCLUDE USING gist (
        table_id WITH =,
        tsrange(date_start, date_end) WITH &&
    );

-- 5. Начальное наполнение справочника клубов ----------------

INSERT INTO clubs (code, title)
VALUES
  ('MIX',       'Микс Afterparty'),
  ('OSOBNYAK',  'Особняк'),
  ('CLUB3',     'Club 3'),
  ('CLUB4',     'Club 4')
ON CONFLICT (code) DO NOTHING; -- Avoid errors if run multiple times during dev

COMMIT;