CREATE TABLE IF NOT EXISTS users
(
    id    UUID   NOT NULL,
    email varchar(254) NOT NULL,
    PRIMARY KEY (id)
);