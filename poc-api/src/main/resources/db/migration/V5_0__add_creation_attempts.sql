ALTER TABLE poc_manager.poc_table
    ADD COLUMN creation_attempts int DEFAULT 0;

ALTER TABLE poc_manager.poc_admin_table
    ADD COLUMN creation_attempts int DEFAULT 0;

ALTER TABLE poc_manager.poc_employee_table
    ADD COLUMN creation_attempts int DEFAULT 0;