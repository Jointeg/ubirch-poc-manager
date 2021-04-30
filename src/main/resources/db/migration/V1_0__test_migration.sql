CREATE TABLE IF NOT EXISTS poc_manager.users
(
    id     UUID         NOT NULL,
    email  varchar(254) NOT NULL,
    status varchar(50)  NOT NULL,
    PRIMARY KEY (id)
);


CREATE TABLE IF NOT EXISTS poc_manager.poc_table (
    id                          UUID            NOT NULL,
    external_id                 varchar(40)     NOT NULL,
    poc_name                    text            NOT NULL,
-- address
    street                      text            NOT NULL,
    house_number                text            NOT NULL,
    additional_address          text,
    zipcode                     int             NOT NULL,
    city                        text            NOT NULL,
    county                      text,
    federal_state               text,
    country                     text,
-- addressEnd
    phone                       text            NOT NULL,
    certify_app                 boolean         NOT NULL,
    logo_url                    text,
    client_cert_required        boolean         NOT NULL,
    data_schema_id              text            NOT NULL,
    extra_config                text,
-- manager
    manager_surname             text            NOT NULL,
    manager_name                text            NOT NULL,
    manager_email               text            NOT NULL,
    manager_mobile_phone        text            NOT NULL,
-- managerEnd
-- addOns
    user_realm_role_name        text,
    user_realm_group_id         UUID,
    device_realm_role_name      text,
    device_realm_group_id       UUID,
    device_id                   UUID,
    client_cert_folder          text,
-- addOnsEnd
    status                      varchar(10)     NOT NULL,
    last_updated                text            NOT NULL,
    created                     text            NOT NULL,
    PRIMARY KEY(id),
    CONSTRAINT unique_poc UNIQUE (external_id, poc_name, street, house_number, zipcode, data_schema_id)
);


CREATE TABLE IF NOT EXISTS poc_manager.poc_status_table (
    poc_id                       UUID            NOT NULL,
    valid_data_schema_group      boolean         NOT NULL,
    user_realm_role_created      boolean         NOT NULL,
    user_realm_group_created     boolean         NOT NULL,
    device_realm_role_created    boolean         NOT NULL,
    device_realm_group_created   boolean         NOT NULL,
    device_created               boolean         NOT NULL,
    client_cert_required         boolean         NOT NULL,
    client_cert_downloaded       boolean,
    client_cert_provided         boolean,
    logo_required                boolean         NOT NULL,
    logo_received                boolean,
    logo_stored                  boolean,
    cert_api_provided            boolean         NOT NULL,
    go_client_provided           boolean         NOT NULL,
    error_messages               text,
    last_updated                 text            NOT NULL,
    created                      text            NOT NULL,
    PRIMARY KEY (poc_id)
);

CREATE TABLE IF NOT EXISTS poc_manager.tenants
(
    id
    UUID
    NOT
    NULL,
    tenant_name
    text
    NOT
    NULL,
    usage_type
    varchar
(
    255
) NOT NULL,
    device_creation_token text NOT NULL,
    certification_creation_token text NOT NULL,
    id_gard_identifier text NOT NULL,
    group_id text NOT NULL,
    organisational_unit_group_id text NOT NULL,
    PRIMARY KEY
(
    id
)
    );
