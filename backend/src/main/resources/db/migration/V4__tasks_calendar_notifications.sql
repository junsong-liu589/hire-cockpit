CREATE TABLE task (
  id CHAR(36) NOT NULL,
  workspace_id BINARY(16) NOT NULL,
  title VARCHAR(180) NOT NULL,
  description TEXT NULL,
  task_type VARCHAR(80) NOT NULL DEFAULT '其他',
  priority VARCHAR(8) NOT NULL DEFAULT 'B',
  due_at TIMESTAMP(6) NULL,
  time_zone VARCHAR(64) NULL,
  job_id CHAR(36) NULL,
  application_id CHAR(36) NULL,
  completed BOOLEAN NOT NULL DEFAULT FALSE,
  completed_at TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at TIMESTAMP(6) NULL,
  PRIMARY KEY (id,workspace_id), INDEX ix_task_due (workspace_id,completed,due_at,deleted_at),
  CONSTRAINT fk_task_workspace FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE CASCADE,
  CONSTRAINT fk_task_job FOREIGN KEY (job_id,workspace_id) REFERENCES job(id,workspace_id),
  CONSTRAINT fk_task_application FOREIGN KEY (application_id,workspace_id) REFERENCES application(id,workspace_id)
) ENGINE=InnoDB;

CREATE TABLE calendar_event (
  id CHAR(36) NOT NULL,
  workspace_id BINARY(16) NOT NULL,
  title VARCHAR(180) NOT NULL,
  event_type VARCHAR(40) NOT NULL DEFAULT 'PERSONAL',
  starts_at TIMESTAMP(6) NOT NULL,
  ends_at TIMESTAMP(6) NULL,
  time_zone VARCHAR(64) NOT NULL DEFAULT 'UTC',
  notes TEXT NULL,
  job_id CHAR(36) NULL,
  application_id CHAR(36) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  deleted_at TIMESTAMP(6) NULL,
  PRIMARY KEY (id,workspace_id), INDEX ix_calendar_start (workspace_id,starts_at,deleted_at),
  CONSTRAINT fk_event_workspace FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE CASCADE,
  CONSTRAINT fk_event_job FOREIGN KEY (job_id,workspace_id) REFERENCES job(id,workspace_id),
  CONSTRAINT fk_event_application FOREIGN KEY (application_id,workspace_id) REFERENCES application(id,workspace_id)
) ENGINE=InnoDB;

CREATE TABLE notification_event (
  id CHAR(36) NOT NULL PRIMARY KEY,
  workspace_id BINARY(16) NOT NULL,
  source_type VARCHAR(24) NOT NULL,
  source_id CHAR(36) NOT NULL,
  reminder_key VARCHAR(32) NOT NULL,
  title VARCHAR(240) NOT NULL,
  due_at TIMESTAMP(6) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  read_at TIMESTAMP(6) NULL,
  UNIQUE KEY uq_notification_dedupe (workspace_id,source_type,source_id,reminder_key),
  INDEX ix_notification_inbox (workspace_id,read_at,due_at),
  CONSTRAINT fk_notification_workspace FOREIGN KEY (workspace_id) REFERENCES workspace(id) ON DELETE CASCADE
) ENGINE=InnoDB;

INSERT INTO dictionary_seed(category,label,stable_stage,color) VALUES
('task_type','投递',NULL,NULL),('task_type','改简历',NULL,NULL),('task_type','备材料',NULL,NULL),('task_type','备笔试/面试',NULL,NULL),('task_type','联系招聘者/内推人',NULL,NULL),('task_type','其他',NULL,NULL);
