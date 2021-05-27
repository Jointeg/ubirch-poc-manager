CREATE INDEX idx_poc_table_poc_name
  ON poc_table(poc_name);

CREATE INDEX idx_poc_table_poc_city
  ON poc_table(city);

CREATE INDEX idx_poc_admin_table_name
  ON poc_admin_table(name);

CREATE INDEX idx_poc_employee_table_name
  ON poc_employee_table(name);