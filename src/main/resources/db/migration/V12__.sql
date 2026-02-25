-- 撤销 V11 中添加的 XSS 防护相关权限配置

-- 删除 questions 表中的 unsafe_xss 字段
ALTER TABLE questions DROP COLUMN unsafe_xss;

-- 删除题组 XSS 权限
DELETE FROM role_permission_mapping 
WHERE permission_id = '88c79d17-0a49-4ec6-8355-f302ad0c04a2';
DELETE FROM permissions 
WHERE id = '88c79d17-0a49-4ec6-8355-f302ad0c04a2';

-- 删除题目 XSS 权限
DELETE FROM role_permission_mapping 
WHERE permission_id = '2a58bc78-a1cc-43d3-8481-808d2fe94004';
DELETE FROM permissions 
WHERE id = '2a58bc78-a1cc-43d3-8481-808d2fe94004';