CREATE TABLE dictionary_seed (
  category VARCHAR(48) NOT NULL,
  label VARCHAR(120) NOT NULL,
  stable_stage VARCHAR(32) NULL,
  color VARCHAR(24) NULL,
  PRIMARY KEY (category, label)
) ENGINE=InnoDB;

INSERT INTO dictionary_seed(category,label,stable_stage,color) VALUES
('company_nature','央企',NULL,NULL),('company_nature','地方国企',NULL,NULL),('company_nature','国有控股',NULL,NULL),('company_nature','民营',NULL,NULL),('company_nature','外资',NULL,NULL),('company_nature','银行',NULL,NULL),('company_nature','证券',NULL,NULL),('company_nature','保险',NULL,NULL),('company_nature','事业单位',NULL,NULL),('company_nature','科研院所',NULL,NULL),('company_nature','高校',NULL,NULL),('company_nature','其他',NULL,NULL),
('job_type','开发',NULL,NULL),('job_type','算法',NULL,NULL),('job_type','人工智能',NULL,NULL),('job_type','数据',NULL,NULL),('job_type','研发',NULL,NULL),('job_type','金融',NULL,NULL),('job_type','财务',NULL,NULL),('job_type','管理',NULL,NULL),('job_type','运营',NULL,NULL),('job_type','销售',NULL,NULL),
('recruitment_type','秋招',NULL,NULL),('recruitment_type','春招',NULL,NULL),('recruitment_type','提前批',NULL,NULL),('recruitment_type','实习',NULL,NULL),('recruitment_type','社招',NULL,NULL),('recruitment_type','长期',NULL,NULL),('recruitment_type','补录',NULL,NULL),
('application_status','待投递','TODO',NULL),('application_status','已收藏','FAVORITE',NULL),('application_status','已投递','APPLICATION',NULL),('application_status','简历筛选中','SCREENING',NULL),('application_status','笔试待考','EXAM',NULL),('application_status','笔试完成','EXAM',NULL),('application_status','一面待面试','INTERVIEW',NULL),('application_status','二面待面试','INTERVIEW',NULL),('application_status','三面待面试','INTERVIEW',NULL),('application_status','谈 Offer','OFFER',NULL),('application_status','已拿 Offer','OFFER',NULL),('application_status','简历挂','CLOSED',NULL),('application_status','笔试挂','CLOSED',NULL),('application_status','面试挂','CLOSED',NULL),('application_status','拒绝 Offer','CLOSED',NULL),('application_status','主动放弃','CLOSED',NULL),('application_status','岗位关闭','CLOSED',NULL);
