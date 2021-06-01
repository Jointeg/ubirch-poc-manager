ALTER TABLE poc_manager.poc_status_table
    ADD COLUMN assigned_trusted_poc_group boolean NOT NULL default false;
