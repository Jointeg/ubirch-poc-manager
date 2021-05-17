CREATE TABLE IF NOT EXISTS poc_manager.users
(
    id     UUID         NOT NULL,
    email  varchar(254) NOT NULL,
    status varchar(50)  NOT NULL,
    PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS poc_manager.poc_table
(
    id                   UUID        NOT NULL,
    tenant_id            UUID        NOT NULL,
    external_id          varchar(40) NOT NULL,
    poc_name             text        NOT NULL,
    street               text        NOT NULL,
    house_number         text        NOT NULL,
    additional_address   text,
    zipcode              int         NOT NULL,
    city                 text        NOT NULL,
    county               text,
    federal_state        text,
    country              text,
    phone                text        NOT NULL,
    certify_app          boolean     NOT NULL,
    logo_url             text,
    client_cert_required boolean     NOT NULL,
    data_schema_id       text        NOT NULL,
    extra_config         text,
    manager_surname      text        NOT NULL,
    manager_name         text        NOT NULL,
    manager_email        text        NOT NULL,
    manager_mobile_phone text        NOT NULL,
    role_name            text        NOT NULL,
    device_group_id      text,
    certify_group_id     text,
    device_id            UUID        NOT NULL,
    client_cert_folder   text,
    status               varchar(10) NOT NULL,
    client_cert          text,
    org_unit_cert_id     UUID,
    last_updated         text        NOT NULL,
    created              text        NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT unique_poc UNIQUE (tenant_id, external_id, data_schema_id)
);


CREATE TABLE IF NOT EXISTS poc_manager.poc_status_table
(
    poc_id                             UUID    NOT NULL,
    certify_role_created               boolean NOT NULL,
    certify_group_created              boolean NOT NULL,
    certify_group_role_assigned        boolean NOT NULL,
    certify_group_tenant_role_assigned boolean NOT NULL,
    device_role_created                boolean NOT NULL,
    device_group_created               boolean NOT NULL,
    device_group_role_assigned         boolean NOT NULL,
    device_group_tenant_role_assigned  boolean NOT NULL,
    device_created                     boolean NOT NULL,
    assigned_data_schema_group         boolean NOT NULL,
    assigned_device_group              boolean NOT NULL,
    client_cert_required               boolean NOT NULL,
    org_unit_cert_id_created           boolean,
    client_cert_created                boolean,
    client_cert_provided               boolean,
    logo_required                      boolean NOT NULL,
    logo_received                      boolean,
    logo_stored                        boolean,
    go_client_provided                 boolean NOT NULL,
    certify_api_provided               boolean NOT NULL,
    error_message                      text,
    last_updated                       text    NOT NULL,
    created                            text    NOT NULL,
    PRIMARY KEY (poc_id)
);


CREATE TABLE IF NOT EXISTS poc_manager.tenants
(
    id                           UUID         NOT NULL,
    tenant_name                  text         NOT NULL,
    usage_type                   varchar(255) NOT NULL,
    device_creation_token        text         NOT NULL,
    certification_creation_token text,
    id_gard_identifier           text,
    certify_group_id             text,
    device_group_id              text,
    client_cert                  text,
    org_cert_id                  UUID         NOT NULL,
    org_unit_cert_id             UUID,
    last_updated                 text         NOT NULL,
    created                      text         NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT unique_tenant UNIQUE (tenant_name)
);

CREATE TABLE IF NOT EXISTS poc_manager.key_hash
(
    hash text  NOT NULL,
    salt bytea NOT NULL,
    PRIMARY KEY (hash)
);
