CREATE TABLE IF NOT EXISTS poc_manager.user_table
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
    poc_type             text        NOT NULL,
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
    client_cert          text,
    org_unit_id          UUID,
    shared_auth_cert_id  UUID,
    data_schema_id       text        NOT NULL,
    extra_config         text,
    manager_surname      text        NOT NULL,
    manager_name         text        NOT NULL,
    manager_email        text        NOT NULL,
    manager_mobile_phone text        NOT NULL,
    role_name            text        NOT NULL,
    device_group_id      text,
    certify_group_id     text,
    admin_group_id       text,
    employee_group_id    text,
    device_id            UUID        NOT NULL,
    client_cert_folder   text,
    status               varchar(10) NOT NULL,
    last_updated         text        NOT NULL,
    created              text        NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT unique_poc UNIQUE (tenant_id, external_id, data_schema_id)
    );

CREATE TABLE IF NOT EXISTS poc_manager.poc_status_table
(
    poc_id                      UUID    NOT NULL,
    certify_role_created        boolean NOT NULL,
    certify_group_created       boolean NOT NULL,
    certify_group_role_assigned boolean NOT NULL,
    admin_group_created         boolean,
    admin_role_assigned         boolean,
    employee_group_created      boolean,
    employee_role_assigned      boolean,
    device_role_created         boolean NOT NULL,
    device_group_created        boolean NOT NULL,
    device_group_role_assigned  boolean NOT NULL,
    device_created              boolean NOT NULL,
    assigned_data_schema_group  boolean NOT NULL,
    assigned_device_group       boolean NOT NULL,
    client_cert_required        boolean NOT NULL,
    org_unit_cert_created       boolean,
    client_cert_created         boolean,
    client_cert_provided        boolean,
    logo_required               boolean NOT NULL,
    logo_received               boolean,
    logo_stored                 boolean,
    go_client_provided          boolean NOT NULL,
    certify_api_provided        boolean NOT NULL,
    error_message               text,
    last_updated                text    NOT NULL,
    created                     text    NOT NULL,
    PRIMARY KEY (poc_id)
    );


CREATE TABLE IF NOT EXISTS poc_manager.tenant_table
(
    id                        UUID         NOT NULL,
    tenant_name               text         NOT NULL,
    usage_type                varchar(255) NOT NULL,
    device_creation_token     text         NOT NULL,
    certify_group_id          text,
    device_group_id           text,
    org_id                    UUID         NOT NULL,
    shared_auth_cert_required boolean,
    org_unit_id               UUID,
    group_id                  UUID,
    shared_auth_cert          text,
    last_updated              text         NOT NULL,
    created                   text         NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT unique_tenant UNIQUE (tenant_name)
    );

CREATE TABLE IF NOT EXISTS poc_manager.key_hash_table
(
    hash text  NOT NULL,
    salt bytea NOT NULL,
    PRIMARY KEY (hash)
    );

CREATE TABLE IF NOT EXISTS poc_manager.poc_admin_table
(
    id                    UUID        NOT NULL,
    poc_id                UUID        NOT NULL,
    tenant_id             UUID        NOT NULL,
    name                  text        NOT NULL,
    surname               text        NOT NULL,
    email                 text        NOT NULL,
    mobile_phone          varchar(20) NOT NULL,
    web_ident_required    boolean     NOt NULL,
    web_ident_initiate_id UUID,
    web_ident_id          text,
    certify_user_id       UUID,
    date_of_birth         varchar(20) NOT NULL,
    status                varchar(10) NOT NULL,
    last_updated          text        NOT NULL,
    created               text        NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT unique_poc_admin UNIQUE (tenant_id, id, poc_id),
    CONSTRAINT unique_poc_admin_email UNIQUE (email),
    FOREIGN KEY (poc_id) REFERENCES poc_manager.poc_table (id)
    );


CREATE TABLE IF NOT EXISTS poc_manager.poc_admin_status_table
(
    poc_admin_id             UUID    NOT NULL,
    web_ident_required       boolean NOT NULL,
    web_ident_initiated      boolean,
    web_ident_success        boolean,
    certify_user_created     boolean NOT NULL,
    keycloak_email_sent      boolean NOT NULL,
    poc_admin_group_assigned boolean NOT NULL,
    invited_to_team_drive    boolean,
    error_message            text,
    last_updated             text    NOT NULL,
    created                  text    NOT NULL,
    PRIMARY KEY (poc_admin_id)
    );

CREATE TABLE IF NOT EXISTS poc_manager.poc_employee_table
(
    id              UUID        NOT NULL,
    poc_id          UUID        NOT NULL,
    tenant_id       UUID        NOT NULL,
    name            text        NOT NULL,
    surname         text        NOT NULL,
    email           text        NOT NULL,
    certify_user_id UUID,
    status          varchar(10) NOT NULL,
    active          boolean     NOT NULL,
    last_updated    text        NOT NULL,
    created         text        NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT unique_poc_employee UNIQUE (email),
    FOREIGN KEY (poc_id) REFERENCES poc_manager.poc_table (id),
    FOREIGN KEY (tenant_id) REFERENCES poc_manager.tenant_table (id)
    );

CREATE TABLE IF NOT EXISTS poc_manager.poc_employee_status_table
(
    poc_employee_id         UUID    NOT NULL,
    certify_user_created    boolean NOT NULL,
    employee_group_assigned boolean NOT NULL,
    keycloak_email_sent     boolean NOT NULL,
    error_message           text,
    last_updated            text    NOT NULL,
    created                 text    NOT NULL,
    PRIMARY KEY (poc_employee_id),
    FOREIGN KEY (poc_employee_id) REFERENCES poc_manager.poc_employee_table (id)
    );
