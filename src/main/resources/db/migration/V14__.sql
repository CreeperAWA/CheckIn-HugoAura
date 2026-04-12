-- 黑名单功能相关表

-- 1. 黑名单表
CREATE TABLE blacklist
(
    id VARCHAR(36) NOT NULL,
    target_id VARCHAR(255) NOT NULL,
    reason TEXT NULL,
    created_at datetime NULL,
    created_by_qq BIGINT NULL,
    PRIMARY KEY (id)
);

ALTER TABLE blacklist
    ADD CONSTRAINT uk_target_id UNIQUE (target_id);

CREATE INDEX idx_blacklist_target_id ON blacklist (target_id);
CREATE INDEX idx_blacklist_created_at ON blacklist (created_at);

-- 2. 黑名单操作日志表
CREATE TABLE blacklist_logs
(
    id VARCHAR(36) NOT NULL,
    action VARCHAR(20) NOT NULL,
    target_id VARCHAR(255) NOT NULL,
    reason TEXT NULL,
    operated_by_qq BIGINT NULL,
    operated_at datetime NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_blacklist_logs_target_id ON blacklist_logs (target_id);
CREATE INDEX idx_blacklist_logs_operated_at ON blacklist_logs (operated_at);

-- 3. 添加黑名单权限组
INSERT IGNORE INTO permission_groups (name, description)
VALUES ('blacklist', '黑名单管理');

-- 4. 添加黑名单相关权限
INSERT IGNORE INTO permissions (id, description, name, group_name)
VALUES 
    ('bl-view-001', '查看黑名单', 'blacklist.view', 'blacklist'),
    ('bl-manage-001', '管理黑名单（增删）', 'blacklist.manage', 'blacklist');

-- 5. 为超级管理员角色添加黑名单权限
INSERT IGNORE INTO role_permission_mapping (role_type, permission_id)
VALUES 
    ('super admin', 'bl-view-001'),
    ('super admin', 'bl-manage-001');
