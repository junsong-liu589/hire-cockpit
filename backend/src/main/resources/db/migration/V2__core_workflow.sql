CREATE TABLE company (
  id CHAR(36) NOT NULL,
  workspace_id BINARY(16) NOT NULL,
  name VARCHAR(180) NOT NULL,
  short_name VARCHAR(100) NULL,
  group_name VARCHAR(180) NULL,
  nature VARCHAR(80) NULL,
  industry VARCHAR(100) NULL,
  region VARCHAR(120) NULL,
  website VARCHAR(500) NULL,
  recruitment_website VARCHAR(500) NULL,
  description TEXT NULL,
  notes TEXT NULL,
  payload JSON NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at TIMESTAMP(6) NULL,
  PRIMARY KEY (id, workspace_id), INDEX ix_company_workspace (workspace_id, deleted_at),
  CONSTRAINT fk_company_workspace FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE job (
  id CHAR(36) NOT NULL,
  workspace_id BINARY(16) NOT NULL,
  company_id CHAR(36) NOT NULL,
  title VARCHAR(180) NOT NULL,
  department VARCHAR(180) NULL,
  job_number VARCHAR(100) NULL,
  recruitment_type VARCHAR(80) NULL,
  batch VARCHAR(100) NULL,
  city VARCHAR(120) NULL,
  degree_requirement VARCHAR(180) NULL,
  major_requirement VARCHAR(500) NULL,
  skill_requirement TEXT NULL,
  salary VARCHAR(120) NULL,
  deadline DATE NULL,
  source_url VARCHAR(1000) NULL,
  original_text MEDIUMTEXT NULL,
  notes TEXT NULL,
  is_favorite BOOLEAN NOT NULL DEFAULT FALSE,
  priority VARCHAR(8) NOT NULL DEFAULT 'B',
  payload JSON NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at TIMESTAMP(6) NULL,
  PRIMARY KEY (id, workspace_id), INDEX ix_job_workspace (workspace_id, deleted_at, deadline),
  CONSTRAINT fk_job_company FOREIGN KEY (company_id, workspace_id) REFERENCES company(id, workspace_id),
  CONSTRAINT fk_job_workspace FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE resume_version (
  id CHAR(36) NOT NULL,
  workspace_id BINARY(16) NOT NULL,
  name VARCHAR(160) NOT NULL,
  direction VARCHAR(120) NULL,
  version VARCHAR(60) NULL,
  notes TEXT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at TIMESTAMP(6) NULL,
  PRIMARY KEY (id, workspace_id),
  CONSTRAINT fk_resume_workspace FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE application (
  id CHAR(36) NOT NULL,
  workspace_id BINARY(16) NOT NULL,
  job_id CHAR(36) NOT NULL,
  resume_version_id CHAR(36) NULL,
  applied_at TIMESTAMP(6) NULL,
  channel VARCHAR(120) NULL,
  platform VARCHAR(120) NULL,
  platform_account VARCHAR(180) NULL,
  referrer VARCHAR(180) NULL,
  notes TEXT NULL,
  current_status VARCHAR(80) NOT NULL DEFAULT '已投递',
  current_stage VARCHAR(32) NOT NULL DEFAULT 'APPLICATION',
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at TIMESTAMP(6) NULL,
  PRIMARY KEY (id, workspace_id), INDEX ix_application_workspace (workspace_id, deleted_at, current_stage),
  CONSTRAINT fk_application_job FOREIGN KEY (job_id, workspace_id) REFERENCES job(id, workspace_id),
  CONSTRAINT fk_application_resume FOREIGN KEY (resume_version_id, workspace_id) REFERENCES resume_version(id, workspace_id),
  CONSTRAINT fk_application_workspace FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE application_status_history (
  id CHAR(36) NOT NULL PRIMARY KEY,
  workspace_id BINARY(16) NOT NULL,
  application_id CHAR(36) NOT NULL,
  status_snapshot VARCHAR(80) NOT NULL,
  stable_stage VARCHAR(32) NOT NULL,
  note TEXT NULL,
  changed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  INDEX ix_status_application (workspace_id, application_id, changed_at),
  CONSTRAINT fk_status_application FOREIGN KEY (application_id, workspace_id) REFERENCES application(id, workspace_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE tag (
  id CHAR(36) NOT NULL,
  workspace_id BINARY(16) NOT NULL,
  name VARCHAR(80) NOT NULL,
  category VARCHAR(40) NOT NULL DEFAULT '岗位',
  color VARCHAR(24) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  deleted_at TIMESTAMP(6) NULL,
  PRIMARY KEY (id, workspace_id), UNIQUE KEY uq_tag_name (workspace_id, name, deleted_at),
  CONSTRAINT fk_tag_workspace FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE job_tag (
  workspace_id BINARY(16) NOT NULL,
  job_id CHAR(36) NOT NULL,
  tag_id CHAR(36) NOT NULL,
  PRIMARY KEY (workspace_id, job_id, tag_id),
  CONSTRAINT fk_job_tag_job FOREIGN KEY (job_id, workspace_id) REFERENCES job(id, workspace_id) ON DELETE CASCADE,
  CONSTRAINT fk_job_tag_tag FOREIGN KEY (tag_id, workspace_id) REFERENCES tag(id, workspace_id) ON DELETE CASCADE
) ENGINE=InnoDB;
