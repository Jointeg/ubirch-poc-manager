CREATE TABLE IF NOT EXISTS poc_manager.users
(
    id     UUID         NOT NULL,
    email  varchar(254) NOT NULL,
    status varchar(50)  NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS poc_manager.tenants
(
    id                           UUID         NOT NULL,
    tenant_name                  text         NOT NULL,
    usage_type                   varchar(255) NOT NULL,
    device_creation_token        text         NOT NULL,
    certification_creation_token text         NOT NULL,
    id_gard_identifier           text         NOT NULL,
    group_id                     text         NOT NULL,
    organisational_unit_group_id text         NOT NULL
)