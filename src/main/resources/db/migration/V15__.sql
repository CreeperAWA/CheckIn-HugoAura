-- 成绩无效化功能相关权限

-- 1. 添加成绩无效化权限
INSERT IGNORE INTO permissions (id, description, name, group_name)
VALUES ('a1b2c3d4-e5f6-7890-abcd-ef1234567890', '手动无效化成绩', 'manual invalid score', 'exam data');

-- 2. 为admin角色添加成绩无效化权限
INSERT IGNORE INTO role_permission_mapping (role_type, permission_id)
VALUES ('admin', 'a1b2c3d4-e5f6-7890-abcd-ef1234567890');

-- 3. 为super admin角色添加成绩无效化权限
INSERT IGNORE INTO role_permission_mapping (role_type, permission_id)
VALUES ('super admin', 'a1b2c3d4-e5f6-7890-abcd-ef1234567890');
