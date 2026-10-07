CREATE TABLE local_pokemon (
    id UUID PRIMARY KEY,
    poke_api_id INTEGER NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    image VARCHAR(2048),
    custom_name VARCHAR(255),
    region VARCHAR(255),
    internal_classification VARCHAR(255),
    snapshot_json TEXT NOT NULL
);
