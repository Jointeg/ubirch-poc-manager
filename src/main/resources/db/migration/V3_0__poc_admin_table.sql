CREATE TABLE IF NOT EXISTS poc_manager.poc_admin_table
(
    id                          UUID            NOT NULL,
    poc_id                      UUID            NOT NULL,
    tenant_id                   UUID            NOT NULL,
    name                        text            NOT NULL,
    surname                     text            NOT NULL,
    email                       text            NOT NULL,
    mobile_phone                varchar(20)     NOT NULL,
    web_ident_required          boolean         NOt NULL,
    web_ident_identifier        boolean,
    certifier_user_id           UUID            NOT NULL,
    date_of_birth               varchar(20)     NOT NULL,
    status                      varchar(10)     NOT NULL,
    last_updated                text            NOT NULL,
    created                     text            NOT NULL,
    PRIMARY KEY(id),
    CONSTRAINT unique_poc_admin UNIQUE (tenant_id, id, poc_id),
    FOREIGN KEY (poc_id) REFERENCES poc_table(id)
    );


CREATE TABLE IF NOT EXISTS poc_manager.poc_admin_status_table
(
    poc_admin_id                 UUID            NOT NULL,
    web_ident_required           boolean         NOT NULL,
    web_ident_triggered          boolean,
    web_ident_identified         boolean,
    certifer_user_created        boolean         NOT NULL,
    email_action_requied         boolean         NOT NULL,
    verify_email_set             boolean         NOT NULL,
    update_password_set          boolean         NOT NULL,
    two_factor_auth_set          boolean         NOT NULL,
    poc_certify_group_assigned   boolean         NOT NULL,
    poc_tenant_group_assigned    boolean         NOT NULL,
    certify_poc_group_assigned   boolean         NOT NULL,
    error_message                text,
    last_updated                 text            NOT NULL,
    created                      text            NOT NULL,
    PRIMARY KEY (poc_admin_id)
    );
