-- 黑名单功能相关表

-- 1. 黑名单表
CREATE TABLE blacklist (
    id VARCHAR(36) PRIMARY KEY,
    target_id VARCHAR(255) NOT NULL COMMENT '被拉黑的ID，通常为QQ号',
    reason TEXT COMMENT '拉黑原因（可选）',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by_qq BIGINT COMMENT '操作人QQ号',
    UNIQUE KEY uk_target_id (target_id)
) COMMENT '黑名单表';

CREATE INDEX idx_blacklist_target_id ON blacklist (target_id);
CREATE INDEX idx_blacklist_created_at ON blacklist (created_at);

-- 2. 黑名单操作日志表
CREATE TABLE blacklist_logs (
    id VARCHAR(36) PRIMARY KEY,
    action VARCHAR(20) NOT NULL COMMENT '操作类型: ADD, REMOVE',
    target_id VARCHAR(255) NOT NULL COMMENT '被操作的ID',
    reason TEXT COMMENT '操作原因',
    operated_by_qq BIGINT COMMENT '操作人QQ号',
    operated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间'
) COMMENT '黑名单操作日志表';

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
