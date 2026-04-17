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

INSERT IGNORE INTO server_setting_items (setting_key, setting_value, clazz)
VALUES ('qqVerify.enabled', 'false', 'java.lang.Boolean'),
       ('qqVerify.validDays', '7', 'java.lang.Integer'),
       ('qqVerify.timeoutAction', 'fail', 'java.lang.String'),
       ('qqVerify.cannotVerifyAction', 'skip', 'java.lang.String'),
       ('qqVerify.customStrings', '[]', 'java.util.List'),
       ('notification.submitFrequency.enabled', 'false', 'java.lang.Boolean'),
       ('notification.submitFrequency.timeWindow', '5', 'java.lang.Integer'),
       ('notification.submitFrequency.threshold', '3', 'java.lang.Integer'),
       ('notification.loginFailure.enabled', 'false', 'java.lang.Boolean'),
       ('notification.loginFailure.threshold', '3', 'java.lang.Integer'),
       ('notification.loginSuccess.enabled', 'false', 'java.lang.Boolean'),
       ('notification.quickSubmit.enabled', 'false', 'java.lang.Boolean'),
       ('notification.quickSubmit.threshold', '1', 'java.lang.Integer'),
       ('notification.paperSubmit.enabled', 'false', 'java.lang.Boolean'),
       ('notification.examStart.enabled', 'false', 'java.lang.Boolean');

INSERT IGNORE INTO permission_groups (name, description)
VALUES ('robotApi', '机器人API管理');

INSERT IGNORE INTO permissions (id, description, name, group_name)
VALUES ('ra-view-001', '查看机器人API设置', 'robotApi.view.setting', 'robotApi'),
       ('ra-manage-001', '修改机器人API设置', 'robotApi.manage.setting', 'robotApi'),
       ('ra-view-002', '查看白名单', 'robotApi.view.whitelist', 'robotApi'),
       ('ra-manage-002', '管理白名单（增删）', 'robotApi.manage.whitelist', 'robotApi');

INSERT IGNORE INTO role_permission_mapping (role_type, permission_id)
VALUES ('super admin', 'ra-view-001'),
       ('super admin', 'ra-manage-001'),
       ('super admin', 'ra-view-002'),
       ('super admin', 'ra-manage-002');
