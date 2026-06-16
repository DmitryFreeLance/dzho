CREATE TABLE IF NOT EXISTS participants (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL UNIQUE,
    first_name TEXT,
    last_name TEXT,
    screen_name TEXT,
    profile_url TEXT NOT NULL,
    avatar_url TEXT,
    source_owner_id INTEGER NOT NULL,
    source_post_id INTEGER NOT NULL,
    art_table TEXT NOT NULL,
    first_comment_id INTEGER NOT NULL UNIQUE,
    first_comment_text TEXT,
    first_comment_at TEXT NOT NULL,
    first_reply_comment_id INTEGER,
    reply_link TEXT,
    reply_mode TEXT NOT NULL,
    duplicate_comments_count INTEGER NOT NULL DEFAULT 0,
    last_seen_comment_id INTEGER NOT NULL,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS comment_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    comment_id INTEGER NOT NULL UNIQUE,
    user_id INTEGER NOT NULL,
    owner_id INTEGER NOT NULL,
    post_id INTEGER NOT NULL,
    parent_comment_id INTEGER,
    comment_text TEXT,
    art_table TEXT NOT NULL,
    vk_created_at TEXT NOT NULL,
    received_at TEXT NOT NULL,
    status TEXT NOT NULL,
    details TEXT,
    reply_comment_id INTEGER
);

CREATE INDEX IF NOT EXISTS idx_comment_events_user_id ON comment_events (user_id);
CREATE INDEX IF NOT EXISTS idx_comment_events_post_id ON comment_events (post_id);

