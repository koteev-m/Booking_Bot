package bot.db.migration

CREATE TABLE IF NOT EXISTS users (
id SERIAL PRIMARY KEY,
telegram_id BIGINT UNIQUE NOT NULL,
first_name TEXT,
last_name TEXT,
username TEXT,
phone TEXT,
language_code VARCHAR(5) DEFAULT 'ru',
loyalty_points INTEGER DEFAULT 0,
created_at TIMESTAMP NOT NULL DEFAULT NOW(),
last_activity_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS clubs (
id SERIAL PRIMARY KEY,
code TEXT UNIQUE NOT NULL,
title TEXT NOT NULL,
description TEXT,
address TEXT,
club_phone TEXT,
working_hours TEXT,
timezone TEXT DEFAULT 'Europe/Moscow',
photo_url TEXT,
floor_plan_image_url TEXT,
is_active BOOLEAN DEFAULT TRUE,
created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS tables (
id SERIAL PRIMARY KEY,
club_id INTEGER REFERENCES clubs(id),
number INTEGER NOT NULL,
seats INTEGER NOT NULL,
description TEXT,
pos_x INTEGER,
pos_y INTEGER,
photo_url TEXT,
is_active BOOLEAN DEFAULT TRUE,
UNIQUE (club_id, number)
);

CREATE TABLE IF NOT EXISTS bookings (
id SERIAL PRIMARY KEY,
club_id INTEGER REFERENCES clubs(id),
table_id INTEGER REFERENCES tables(id),
user_id INTEGER REFERENCES users(id),
guests_count INTEGER NOT NULL,
date_start TIMESTAMP NOT NULL,
date_end TIMESTAMP NOT NULL,
status VARCHAR(20) NOT NULL DEFAULT 'NEW',
comment TEXT,
guest_name TEXT,
guest_phone TEXT,
loyalty_points_earned INTEGER,
feedback_rating INTEGER,
feedback_comment TEXT,
created_at TIMESTAMP NOT NULL DEFAULT NOW(),
updated_at TIMESTAMP
);