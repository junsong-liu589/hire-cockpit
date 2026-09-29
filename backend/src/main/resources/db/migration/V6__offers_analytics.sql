CREATE TABLE offer (
  id CHAR(36) NOT NULL,
  workspace_id BINARY(16) NOT NULL,
  application_id CHAR(36) NOT NULL,
  base_salary DECIMAL(12,2) NULL,
  bonus DECIMAL(12,2) NULL,
  equity DECIMAL(12,2) NULL,
  benefits JSON NULL,
  evaluations JSON NULL,
  work_city VARCHAR(120) NULL,
  work_mode VARCHAR(40) NULL,
  received_at DATE NULL,
  response_deadline DATE NULL,
  decision VARCHAR(32) NOT NULL DEFAULT '待决定',
  notes TEXT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at TIMESTAMP(6) NULL,
  PRIMARY KEY (id,workspace_id), INDEX ix_offer_workspace (workspace_id,decision,deleted_at),
  CONSTRAINT fk_offer_workspace FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE CASCADE,
  CONSTRAINT fk_offer_application FOREIGN KEY (application_id,workspace_id) REFERENCES application(id,workspace_id)
) ENGINE=InnoDB;

CREATE TABLE offer_comparison_preference (
  workspace_id BINARY(16) NOT NULL PRIMARY KEY,
  weights JSON NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_offer_preference_workspace FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE CASCADE
) ENGINE=InnoDB;
