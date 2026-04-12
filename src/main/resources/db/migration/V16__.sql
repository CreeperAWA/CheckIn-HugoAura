-- 答题次数限制功能相关表

-- 1. 答题次数限制白名单表
CREATE TABLE answer_limit_whitelist
(
    id VARCHAR(36) NOT NULL,
    qq VARCHAR(255) NOT NULL,
    reason TEXT NULL,
    created_at datetime NULL,
    created_by_qq BIGINT NULL,
    PRIMARY KEY (id)
);

ALTER TABLE answer_limit_whitelist
    ADD CONSTRAINT uk_whitelist_qq UNIQUE (qq);

CREATE INDEX idx_whitelist_qq ON answer_limit_whitelist (qq);
CREATE INDEX idx_whitelist_created_at ON answer_limit_whitelist (created_at);

-- 3. 答题次数限制设置表（使用现有 server_setting_items 表）
-- 添加默认答题次数限制设置
INSERT IGNORE INTO server_setting_items (setting_key, setting_value, clazz)
VALUES 
    ('answerLimit.maxCount', '5', 'java.lang.Integer');

-- 4. 添加答题次数限制权限组
INSERT IGNORE INTO permission_groups (name, description)
VALUES ('answerLimit', '答题次数限制管理');

-- 5. 添加答题次数限制相关权限
INSERT IGNORE INTO permissions (id, description, name, group_name)
VALUES 
    ('al-view-001', '查看答题次数限制设置', 'answerLimit.view.setting', 'answerLimit'),
    ('al-manage-001', '修改答题次数限制设置', 'answerLimit.manage.setting', 'answerLimit'),
    ('al-view-002', '查询用户答题次数', 'answerLimit.view.count', 'answerLimit'),
    ('al-view-003', '查询白名单', 'answerLimit.view.whitelist', 'answerLimit'),
    ('al-manage-002', '管理白名单（增删）', 'answerLimit.manage.whitelist', 'answerLimit'),
    ('al-manage-003', '删除答题记录', 'answerLimit.manage.record', 'answerLimit');

-- 6. 为超级管理员角色添加答题次数限制权限
INSERT IGNORE INTO role_permission_mapping (role_type, permission_id)
VALUES 
    ('super admin', 'al-view-001'),
    ('super admin', 'al-manage-001'),
    ('super admin', 'al-view-002'),
    ('super admin', 'al-view-003'),
    ('super admin', 'al-manage-002'),
    ('super admin', 'al-manage-003');
