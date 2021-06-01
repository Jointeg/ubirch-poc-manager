ALTER TABLE poc_manager.poc_status_table
  DROP COLUMN logo_received;

CREATE TABLE IF NOT EXISTS poc_manager.poc_logo_table
(
    poc_id UUID NOT NULL,
    img bytea NOT NULL,
    PRIMARY KEY(poc_id),
    FOREIGN KEY (poc_id) REFERENCES poc_manager.poc_table (id)
)