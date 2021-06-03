ALTER TABLE poc_manager.poc_admin_table
    ADD COLUMN web_authn_disconnected timestamp;

ALTER TABLE poc_manager.poc_employee_table
    ADD COLUMN web_authn_disconnected timestamp;
