ALTER TABLE poc_manager.poc_table
    ADD COLUMN admin_group_id    text,
    ADD COLUMN employee_group_id text;

ALTER TABLE poc_manager.poc_employee_status_table
    DROP poc_tenant_group_assigned,
    DROP poc_group_assigned;


ALTER TABLE poc_manager.poc_status_table
    ADD COLUMN admin_group_created    boolean,
    ADD COLUMN admin_role_assigned    boolean,
    ADD COLUMN employee_group_created boolean,
    ADD COLUMN employee_role_assigned boolean,
    DROP COLUMN certify_group_tenant_role_assigned,
    DROP COLUMN device_group_tenant_role_assigned;

ALTER TABLE poc_manager.poc_admin_status_table
    DROP COLUMN poc_certify_group_assigned,
    DROP COLUMN poc_tenant_group_assigned;



