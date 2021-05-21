ALTER TABLE poc_manager.poc_admin_status_table
    DROP COLUMN verify_email_set,
    DROP COLUMN update_password_set,
    DROP COLUMN two_factor_auth_set;

ALTER TABLE poc_manager.poc_admin_status_table
    RENAME certify_poc_group_assigned TO poc_admin_group_assigned;

ALTER TABLE poc_manager.poc_admin_status_table
    RENAME web_ident_identified TO web_ident_success;

ALTER TABLE poc_manager.poc_admin_status_table
    RENAME email_action_required TO keycloak_email_sent;

ALTER TABLE poc_manager.poc_admin_status_table
    RENAME certifier_user_created TO certify_user_created;

ALTER TABLE poc_manager.poc_admin_status_table
    ADD COLUMN invited_to_team_drive boolean;

ALTER TABLE poc_manager.poc_admin_status_table
    RENAME web_ident_triggered TO web_ident_initiated;

ALTER TABLE poc_manager.poc_admin_table
    RENAME certifier_user_id TO certify_user_id;

ALTER TABLE poc_manager.poc_admin_table
    ALTER COLUMN certify_user_id DROP NOT NULL;

ALTER TABLE poc_manager.poc_admin_table ADD CONSTRAINT unique_poc_admin_email UNIQUE (email);
