CREATE TABLE collection_rule (
  id CHAR(36) NOT NULL,
  workspace_id BINARY(16) NOT NULL,
  name VARCHAR(120) NOT NULL,
  keywords JSON NOT NULL,
  weight DECIMAL(5,2) NOT NULL DEFAULT 1.00,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at TIMESTAMP(6) NULL,
  PRIMARY KEY (id,workspace_id), INDEX ix_collection_rule_workspace (workspace_id,name,deleted_at),
  CONSTRAINT ck_collection_weight CHECK (weight BETWEEN 0 AND 100),
  CONSTRAINT fk_collection_rule_workspace FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE CASCADE
) ENGINE=InnoDB;
