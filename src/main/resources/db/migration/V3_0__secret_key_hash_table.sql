CREATE TABLE IF NOT EXISTS poc_manager.key_hash
(
    hash text NOT NULL,
    salt bytea NOT NULL,
    PRIMARY KEY (hash)
);
