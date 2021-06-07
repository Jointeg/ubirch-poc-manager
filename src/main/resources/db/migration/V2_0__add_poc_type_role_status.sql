ALTER TABLE poc_manager.poc_status_table
  ADD COLUMN poc_type_role_created boolean;

ALTER TABLE poc_manager.poc_status_table
    ADD COLUMN poc_type_group_role_assigned boolean;