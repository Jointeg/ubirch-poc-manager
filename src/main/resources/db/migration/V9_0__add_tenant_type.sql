ALTER TABLE poc_manager.tenant_table
    ADD COLUMN tenant_type varchar(255) NOT NULL,
    ADD COLUMN tenant_type_group_id varchar(36);