CREATE TABLE IF NOT EXISTS poc_manager.users
(
    id     UUID         NOT NULL,
    email  varchar(254) NOT NULL,
    status varchar(50)  NOT NULL,
    PRIMARY KEY (id)
);