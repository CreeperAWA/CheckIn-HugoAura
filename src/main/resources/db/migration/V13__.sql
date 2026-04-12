-- 请求频率限流功能相关表

-- 1. 限流规则配置表
CREATE TABLE rate_limit_rules
(
    id VARCHAR(36) NOT NULL,
    dimension VARCHAR(20) NOT NULL,
    enabled TINYINT NOT NULL,
    time_window_seconds INT NOT NULL,
    max_requests INT NOT NULL,
    response_strategy VARCHAR(20) NOT NULL,
    custom_message TEXT NULL,
    base_delay_ms INT NOT NULL,
    priority INT NOT NULL,
    limit_duration_seconds INT NOT NULL,
    created_at datetime NULL,
    updated_at datetime NULL,
    PRIMARY KEY (id)
);

-- 2. 限流白名单表
CREATE TABLE rate_limit_whitelist
(
    id VARCHAR(36) NOT NULL,
    dimension VARCHAR(20) NOT NULL,
    whitelist_value VARCHAR(255) NOT NULL,
    description VARCHAR(255) NULL,
    created_at datetime NULL,
    PRIMARY KEY (id)
);

ALTER TABLE rate_limit_whitelist
    ADD CONSTRAINT UK_rate_limit_whitelist_dimension_value UNIQUE (dimension, whitelist_value);

-- 3. 限流日志表
CREATE TABLE rate_limit_logs
(
    id VARCHAR(36) NOT NULL,
    ip_address VARCHAR(45) NULL,
    cookie_value VARCHAR(255) NULL,
    qq_number BIGINT NULL,
    oauth_info VARCHAR(255) NULL,
    request_path VARCHAR(500) NOT NULL,
    request_method VARCHAR(10) NOT NULL,
    triggered_rule_id VARCHAR(36) NULL,
    triggered_dimension VARCHAR(20) NULL,
    response_action VARCHAR(50) NOT NULL,
    created_at datetime NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_ip ON rate_limit_logs (ip_address);
CREATE INDEX idx_qq ON rate_limit_logs (qq_number);
CREATE INDEX idx_created_at ON rate_limit_logs (created_at);
CREATE INDEX idx_triggered_dimension ON rate_limit_logs (triggered_dimension);

-- 4. 初始化默认的限流规则数据
INSERT INTO rate_limit_rules (id, dimension, enabled, time_window_seconds, max_requests, response_strategy, custom_message, base_delay_ms, priority, limit_duration_seconds) VALUES
('ip-rule-001', 'IP', 0, 60, 100, 'RETURN_429', NULL, 1000, 10, 300),
('cookie-rule-001', 'COOKIE', 0, 60, 100, 'RETURN_429', NULL, 1000, 20, 300),
('qq-rule-001', 'QQ', 0, 60, 100, 'RETURN_429', NULL, 1000, 30, 300),
('oauth-rule-001', 'OAUTH', 0, 60, 100, 'RETURN_429', NULL, 1000, 40, 300);

-- 5. 添加限流权限组和权限
-- 添加限流权限组
INSERT IGNORE INTO permission_groups (name, description)
VALUES ('rate limit', '限流管理');

-- 添加限流相关权限
INSERT IGNORE INTO permissions (id, description, name, group_name)
VALUES ('a1b2c3d4-e5f6-4g7h-8i9j-0k1l2m3n4o5p', '绕过限流约束', 'BYPASS_RATE_LIMIT', 'rate limit'),
       ('b2c3d4e5-f6g7-8h9i-0j1k-2l3m4n5o6p7q', '查看限流监控', 'VIEW_RATE_LIMIT_MONITOR', 'rate limit'),
       ('c3d4e5f6-g7h8-9i0j-1k2l-3m4n5o6p7q8r', '查看限流配置', 'VIEW_RATE_LIMIT_CONFIG', 'rate limit'),
       ('d4e5f6g7-h8i9-0j1k-2l3m-4n5o6p7q8r9s', '修改限流配置', 'MODIFY_RATE_LIMIT_CONFIG', 'rate limit');

-- 为超级管理员角色添加限流权限
INSERT IGNORE INTO role_permission_mapping (role_type, permission_id)
VALUES ('super admin', 'a1b2c3d4-e5f6-4g7h-8i9j-0k1l2m3n4o5p'),
       ('super admin', 'b2c3d4e5-f6g7-8h9i-0j1k-2l3m4n5o6p7q'),
       ('super admin', 'c3d4e5f6-g7h8-9i0j-1k2l-3m4n5o6p7q8r'),
       ('super admin', 'd4e5f6g7-h8i9-0j1k-2l3m-4n5o6p7q8r9s');
