CREATE TABLE whitelist
(
    id            CHAR(36)     NOT NULL,
    target_id     VARCHAR(255) NOT NULL,
    reason        TEXT         NULL,
    created_at    datetime     NULL,
    created_by_qq BIGINT       NULL,
    PRIMARY KEY (id)
);

CREATE TABLE qq_verify_record
(
    id             CHAR(36)     NOT NULL,
    qq             VARCHAR(255) NOT NULL,
    verify_content TEXT         NOT NULL,
    verified_at    datetime     NULL,
    status         VARCHAR(50)  NOT NULL,
    message        TEXT         NULL,
    PRIMARY KEY (id)
);

-- 创建 third_party_api_sid_tokens 表
CREATE TABLE third_party_api_sid_tokens
(
    id                 CHAR(36)      NOT NULL PRIMARY KEY,
    sid                CHAR(36)      NULL,
    `description`      VARCHAR(1024) NULL,
    generate_by_userqq BIGINT        NULL,
    generate_time      datetime      NULL,
    token              VARCHAR(512)  NULL
);

-- Rename robot_tokens table to http_api_tokens
ALTER TABLE robot_tokens RENAME TO http_api_tokens;

-- Remove created_at and updated_at columns from rate_limit_rules table
ALTER TABLE rate_limit_rules DROP COLUMN created_at;
ALTER TABLE rate_limit_rules DROP COLUMN updated_at;

INSERT IGNORE INTO server_setting_items (setting_key, setting_value, clazz)
VALUES ('thirdPartyApi.qqVerify.enabled', 'false', 'java.lang.Boolean'),
       ('thirdPartyApi.qqVerify.validDays', '3', 'java.lang.Integer'),
       ('thirdPartyApi.qqVerify.timeoutAction', '"fail"', 'java.lang.String'),
       ('thirdPartyApi.qqVerify.cannotVerifyAction', '"skip"', 'java.lang.String'),
       ('thirdPartyApi.qqVerify.customStrings', '[]', 'java.util.ArrayList'),
       ('thirdPartyApi.qqVerify.guideMessage', '"请按照以下步骤进行验证：\\n1. 打开验证页面\\n2. 输入验证内容\\n3. 点击验证按钮"', 'java.lang.String'),
       ('thirdPartyApi.qqVerify.whitelist', '[]', 'java.util.ArrayList'),
       ('thirdPartyApi.notification.submitFrequency.enabled', 'false', 'java.lang.Boolean'),
       ('thirdPartyApi.notification.submitFrequency.timeWindow', '5', 'java.lang.Integer'),
       ('thirdPartyApi.notification.submitFrequency.threshold', '3', 'java.lang.Integer'),
       ('thirdPartyApi.notification.loginFailure.enabled', 'false', 'java.lang.Boolean'),
       ('thirdPartyApi.notification.loginSuccess.enabled', 'false', 'java.lang.Boolean'),
       ('thirdPartyApi.notification.quickSubmit.enabled', 'false', 'java.lang.Boolean'),
       ('thirdPartyApi.notification.quickSubmit.threshold', '1', 'java.lang.Integer'),
       ('thirdPartyApi.notification.paperSubmit.enabled', 'false', 'java.lang.Boolean'),
       ('thirdPartyApi.notification.examStart.enabled', 'false', 'java.lang.Boolean');

INSERT IGNORE INTO permission_groups (name, description)
VALUES ('thirdPartyApi', '第三方API管理');

INSERT IGNORE INTO permissions (id, description, name, group_name)
VALUES ('ra-view-001', '查看第三方API设置', 'thirdPartyApi.view.setting', 'thirdPartyApi'),
       ('ra-manage-001', '修改第三方API设置', 'thirdPartyApi.manage.setting', 'thirdPartyApi'),
       ('ra-view-002', '查看白名单', 'thirdPartyApi.view.whitelist', 'thirdPartyApi'),
       ('ra-manage-002', '管理白名单（增删）', 'thirdPartyApi.manage.whitelist', 'thirdPartyApi');

INSERT IGNORE INTO role_permission_mapping (role_type, permission_id)
VALUES ('super admin', 'ra-view-001'),
       ('super admin', 'ra-manage-001'),
       ('super admin', 'ra-view-002'),
       ('super admin', 'ra-manage-002');
