CREATE TABLE stored_file (
  id CHAR(36) NOT NULL,
  workspace_id BINARY(16) NOT NULL,
  original_name VARCHAR(255) NOT NULL,
  media_type VARCHAR(80) NOT NULL,
  file_size BIGINT NOT NULL,
  sha256 CHAR(64) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  deleted_at TIMESTAMP(6) NULL,
  PRIMARY KEY (id,workspace_id), INDEX ix_file_workspace (workspace_id,deleted_at),
  CONSTRAINT fk_file_workspace FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE CASCADE
) ENGINE=InnoDB;

ALTER TABLE resume_version ADD COLUMN stored_file_id CHAR(36) NULL AFTER notes;
ALTER TABLE resume_version ADD CONSTRAINT fk_resume_file FOREIGN KEY (stored_file_id,workspace_id) REFERENCES stored_file(id,workspace_id);

CREATE TABLE profile_entry (
  id CHAR(36) NOT NULL,
  workspace_id BINARY(16) NOT NULL,
  section_key VARCHAR(32) NOT NULL,
  title VARCHAR(180) NOT NULL,
  encrypted_payload MEDIUMBLOB NOT NULL,
  visible_fields JSON NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at TIMESTAMP(6) NULL,
  PRIMARY KEY (id,workspace_id), INDEX ix_profile_section (workspace_id,section_key,deleted_at),
  CONSTRAINT fk_profile_workspace FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE exam (
  id CHAR(36) NOT NULL,
  workspace_id BINARY(16) NOT NULL,
  application_id CHAR(36) NULL,
  platform VARCHAR(140) NULL,
  starts_at TIMESTAMP(6) NULL,
  time_zone VARCHAR(64) NULL,
  exam_url VARCHAR(1000) NULL,
  admission_url VARCHAR(1000) NULL,
  score DECIMAL(8,2) NULL,
  result VARCHAR(80) NULL,
  reflection TEXT NULL,
  notes TEXT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at TIMESTAMP(6) NULL,
  PRIMARY KEY (id,workspace_id), INDEX ix_exam_date (workspace_id,starts_at,deleted_at),
  CONSTRAINT fk_exam_workspace FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE CASCADE,
  CONSTRAINT fk_exam_application FOREIGN KEY (application_id,workspace_id) REFERENCES application(id,workspace_id)
) ENGINE=InnoDB;

CREATE TABLE interview (
  id CHAR(36) NOT NULL,
  workspace_id BINARY(16) NOT NULL,
  application_id CHAR(36) NOT NULL,
  round_name VARCHAR(80) NOT NULL,
  starts_at TIMESTAMP(6) NULL,
  time_zone VARCHAR(64) NULL,
  modality VARCHAR(40) NULL,
  location VARCHAR(300) NULL,
  interviewer VARCHAR(160) NULL,
  question_notes JSON NULL,
  rating TINYINT NULL,
  result VARCHAR(80) NULL,
  review TEXT NULL,
  notes TEXT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at TIMESTAMP(6) NULL,
  PRIMARY KEY (id,workspace_id), INDEX ix_interview_date (workspace_id,starts_at,deleted_at),
  CONSTRAINT ck_interview_rating CHECK (rating IS NULL OR (rating BETWEEN 1 AND 10)),
  CONSTRAINT fk_interview_workspace FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE CASCADE,
  CONSTRAINT fk_interview_application FOREIGN KEY (application_id,workspace_id) REFERENCES application(id,workspace_id)
) ENGINE=InnoDB;

CREATE TABLE experience_note (
  id CHAR(36) NOT NULL,
  workspace_id BINARY(16) NOT NULL,
  company_id CHAR(36) NULL,
  job_id CHAR(36) NULL,
  round_name VARCHAR(80) NULL,
  title VARCHAR(180) NOT NULL,
  questions JSON NULL,
  answers JSON NULL,
  tags JSON NULL,
  source VARCHAR(500) NULL,
  used_count INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at TIMESTAMP(6) NULL,
  PRIMARY KEY (id,workspace_id), INDEX ix_experience_company (workspace_id,company_id,deleted_at),
  CONSTRAINT fk_experience_workspace FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE CASCADE,
  CONSTRAINT fk_experience_company FOREIGN KEY (company_id,workspace_id) REFERENCES company(id,workspace_id),
  CONSTRAINT fk_experience_job FOREIGN KEY (job_id,workspace_id) REFERENCES job(id,workspace_id)
) ENGINE=InnoDB;
