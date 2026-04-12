-- 请求频率限流功能相关表

-- 1. 限流规则配置表
CREATE TABLE rate_limit_rules (
    id VARCHAR(36) PRIMARY KEY,
    dimension VARCHAR(20) NOT NULL COMMENT '限流维度: IP, COOKIE, QQ, OAUTH',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    time_window_seconds INT NOT NULL DEFAULT 60 COMMENT '时间窗口（秒）',
    max_requests INT NOT NULL DEFAULT 100 COMMENT '时间窗口内最大请求数',
    response_strategy VARCHAR(20) NOT NULL DEFAULT 'RETURN_429' COMMENT '响应策略: RETURN_429, CUSTOM_MESSAGE, PROGRESSIVE_DELAY',
    custom_message TEXT COMMENT '自定义提示信息（response_strategy为CUSTOM_MESSAGE时使用）',
    base_delay_ms INT NOT NULL DEFAULT 1000 COMMENT '基础延迟毫秒数（PROGRESSIVE_DELAY策略使用）',
    priority INT NOT NULL DEFAULT 0 COMMENT '规则优先级，数字越大优先级越高',
    limit_duration_seconds INT NOT NULL DEFAULT 300 COMMENT '限流持续时间（秒）',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. 限流白名单表
CREATE TABLE rate_limit_whitelist (
    id VARCHAR(36) PRIMARY KEY,
    dimension VARCHAR(20) NOT NULL COMMENT '白名单维度: IP, COOKIE, OAUTH',
    whitelist_value VARCHAR(255) NOT NULL COMMENT '白名单值',
    description VARCHAR(255) COMMENT '描述说明',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (dimension, whitelist_value)
);

-- 3. 限流日志表
CREATE TABLE rate_limit_logs (
    id VARCHAR(36) PRIMARY KEY,
    ip_address VARCHAR(45) COMMENT 'IP 地址',
    cookie_value VARCHAR(255) COMMENT 'Cookie 值',
    qq_number BIGINT COMMENT 'QQ 号',
    oauth_info VARCHAR(255) COMMENT 'OAuth 信息',
    request_path VARCHAR(500) NOT NULL COMMENT '请求路径',
    request_method VARCHAR(10) NOT NULL COMMENT '请求方法',
    triggered_rule_id VARCHAR(36) COMMENT '触发的规则 ID',
    triggered_dimension VARCHAR(20) COMMENT '触发的维度',
    response_action VARCHAR(50) NOT NULL COMMENT '执行的响应动作',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
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
