ALTER TABLE poc_manager.poc_admin_table
    ADD COLUMN web_ident_initiate_id UUID;

ALTER TABLE poc_manager.poc_admin_table
    RENAME web_ident_identifier TO web_ident_id;
ALTER TABLE poc_manager.poc_admin_table
    ALTER COLUMN web_ident_id TYPE text;