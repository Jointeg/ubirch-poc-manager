CREATE TABLE IF NOT EXISTS poc_manager.poc_employee_table
(
    id                          UUID            NOT NULL,
    poc_id                      UUID            NOT NULL,
    tenant_id                   UUID            NOT NULL,
    name                        text            NOT NULL,
    surname                     text            NOT NULL,
    email                       text            NOT NULL,
    certify_user_id             UUID,
    status                      varchar(10)     NOT NULL,
    active                      boolean         NOT NULL,
    last_updated                text            NOT NULL,
    created                     text            NOT NULL,
    PRIMARY KEY(id),
    CONSTRAINT unique_poc_employee UNIQUE (email),
    FOREIGN KEY (poc_id) REFERENCES poc_table(id)
);

CREATE TABLE IF NOT EXISTS poc_manager.poc_employee_status_table
(
    poc_employee_id              UUID            NOT NULL,
    certify_user_created         boolean         NOT NULL,
    poc_tenant_group_assigned    boolean         NOT NULL,
    poc_group_assigned           boolean         NOT NULL,
    employee_group_assigned      boolean         NOT NULL,
    keycloak_email_sent          boolean         NOT NULL,
    error_message                text,
    last_updated                 text            NOT NULL,
    created                      text            NOT NULL,
    PRIMARY KEY (poc_employee_id),
    FOREIGN KEY (poc_employee_id) REFERENCES poc_employee_table(id)
);
