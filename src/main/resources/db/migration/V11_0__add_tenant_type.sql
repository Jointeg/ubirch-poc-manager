ALTER TABLE poc_manager.tenant_table
    ADD COLUMN tenant_type varchar(255) NOT NULL,
    ADD COLUMN tenant_type_group_id varchar(36);

ALTER TABLE poc_manager.poc_table
    ADD COLUMN poc_type_group_id text;

ALTER TABLE poc_manager.poc_status_table
    ADD COLUMN poc_type_group_created boolean;