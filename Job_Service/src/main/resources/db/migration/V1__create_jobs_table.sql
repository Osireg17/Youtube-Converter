CREATE TABLE jobs (
    id                UUID        PRIMARY KEY,
    youtube_url       VARCHAR     NOT NULL,
    output_format     SMALLINT    NOT NULL,
    status            SMALLINT    NOT NULL DEFAULT 0,
    storage_object_key VARCHAR,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL
);
