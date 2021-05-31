ALTER TABLE poc_manager.poc_table
    DROP CONSTRAINT unique_poc,
    DROP COLUMN data_schema_id,
    ADD CONSTRAINT unique_poc_combination unique(tenant_id, external_id, poc_type);
