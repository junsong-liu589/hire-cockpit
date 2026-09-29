CREATE TABLE workspace (
  id BINARY(16) NOT NULL PRIMARY KEY,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;

CREATE TABLE workspace_credential (
  id BINARY(16) NOT NULL PRIMARY KEY,
  workspace_id BINARY(16) NOT NULL,
  credential_hash BINARY(32) NOT NULL UNIQUE,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  expires_at TIMESTAMP(6) NULL,
  CONSTRAINT fk_credential_workspace FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE CASCADE,
  INDEX ix_credential_workspace (workspace_id)
) ENGINE=InnoDB;

CREATE TABLE dictionary_item (
  id BINARY(16) NOT NULL PRIMARY KEY,
  workspace_id BINARY(16) NOT NULL,
  category VARCHAR(48) NOT NULL,
  label VARCHAR(120) NOT NULL,
  stable_stage VARCHAR(32) NULL,
  color VARCHAR(24) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at TIMESTAMP(6) NULL,
  CONSTRAINT fk_dictionary_workspace FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE CASCADE,
  INDEX ix_dictionary_workspace_category (workspace_id, category, deleted_at)
) ENGINE=InnoDB;

CREATE TABLE workspace_setting (
  workspace_id BINARY(16) NOT NULL,
  setting_key VARCHAR(80) NOT NULL,
  setting_value JSON NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (workspace_id, setting_key),
  CONSTRAINT fk_setting_workspace FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE CASCADE
) ENGINE=InnoDB;
