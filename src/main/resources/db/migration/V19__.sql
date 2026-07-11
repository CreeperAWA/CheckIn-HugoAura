-- V19: Unify all permission/group names to camelCase dot-notation key-name format

-- 1. Update permissions.group_name (foreign key reference) BEFORE renaming groups
UPDATE permissions SET group_name = 'manageUser' WHERE group_name = 'manage user';
UPDATE permissions SET group_name = 'questionGroup' WHERE group_name = 'question group';
UPDATE permissions SET group_name = 'examData' WHERE group_name = 'exam data';
UPDATE permissions SET group_name = 'requestRecord' WHERE group_name = 'request record';
UPDATE permissions SET group_name = 'rateLimit' WHERE group_name = 'rate limit';

-- 2. Rename permission_groups (primary key)
UPDATE permission_groups SET name = 'manageUser' WHERE name = 'manage user';
UPDATE permission_groups SET name = 'questionGroup' WHERE name = 'question group';
UPDATE permission_groups SET name = 'examData' WHERE name = 'exam data';
UPDATE permission_groups SET name = 'requestRecord' WHERE name = 'request record';
UPDATE permission_groups SET name = 'rateLimit' WHERE name = 'rate limit';

-- 3. Rename permissions: manageUser group
UPDATE permissions SET name = 'manageUser.create' WHERE name = 'create user';
UPDATE permissions SET name = 'manageUser.changeState' WHERE name = 'change user state';
UPDATE permissions SET name = 'manageUser.delete' WHERE name = 'delete user';
UPDATE permissions SET name = 'manageUser.changeName' WHERE name = 'change user name';

-- 4. Rename permissions: partition group
UPDATE permissions SET name = 'partition.delete' WHERE name = 'delete partition';
UPDATE permissions SET name = 'partition.create' WHERE name = 'create partition';
UPDATE permissions SET name = 'partition.editName' WHERE name = 'edit partition name';

-- 5. Rename permissions: question group
UPDATE permissions SET name = 'question.editOthers' WHERE name = 'edit others questions';
UPDATE permissions SET name = 'question.enableDisable' WHERE name = 'enable and disable questions';
UPDATE permissions SET name = 'question.createEditOwn' WHERE name = 'create and edit owns questions';
UPDATE permissions SET name = 'question.deleteOthers' WHERE name = 'delete others questions';
UPDATE permissions SET name = 'question.deleteOwn' WHERE name = 'delete owns questions';
UPDATE permissions SET name = 'question.changeAuthor' WHERE name = 'change question author';
UPDATE permissions SET name = 'question.enableDisableXss' WHERE name = 'enable and disable unsafe xss for questions';

-- 6. Rename permissions: questionGroup group
UPDATE permissions SET name = 'questionGroup.editOthers' WHERE name = 'edit others question groups';
UPDATE permissions SET name = 'questionGroup.enableDisable' WHERE name = 'enable and disable question groups';
UPDATE permissions SET name = 'questionGroup.createEditOwn' WHERE name = 'create and edit owns question groups';
UPDATE permissions SET name = 'questionGroup.deleteOthers' WHERE name = 'delete others question groups';
UPDATE permissions SET name = 'questionGroup.deleteOwn' WHERE name = 'delete owns question groups';
UPDATE permissions SET name = 'questionGroup.changeAuthor' WHERE name = 'change question group author';
UPDATE permissions SET name = 'questionGroup.enableDisableXss' WHERE name = 'enable and disable unsafe xss for question groups';

-- 7. Rename permissions: role group
UPDATE permissions SET name = 'role.delete' WHERE name = 'delete role';
UPDATE permissions SET name = 'role.create' WHERE name = 'create role';
UPDATE permissions SET name = 'role.editPermission' WHERE name = 'edit permission';
UPDATE permissions SET name = 'role.updateLevel' WHERE name = 'update role level';
UPDATE permissions SET name = 'role.operateAdmin' WHERE name = 'operate role admin';
UPDATE permissions SET name = 'role.operateSuperAdmin' WHERE name = 'operate role super admin';
UPDATE permissions SET name = 'role.operateUser' WHERE name = 'operate role user';
UPDATE permissions SET name = 'role.operateAdvancedUser' WHERE name = 'operate role advanced user';

-- 8. Rename permissions: examData group
UPDATE permissions SET name = 'examData.get' WHERE name = 'get exam data';
UPDATE permissions SET name = 'examData.getSubmission' WHERE name = 'get exam submission data';
UPDATE permissions SET name = 'examData.manualInvalidExam' WHERE name = 'manual invalid exam';
UPDATE permissions SET name = 'examData.manualInvalidScore' WHERE name = 'manual invalid score';

-- 9. Rename permissions: requestRecord group
UPDATE permissions SET name = 'requestRecord.get' WHERE name = 'get request records';

-- 10. Rename permissions: setting group
UPDATE permissions SET name = 'setting.saveAdvance' WHERE name = 'save advance setting';
UPDATE permissions SET name = 'setting.saveFacade' WHERE name = 'save facade setting';
UPDATE permissions SET name = 'setting.saveGenerating' WHERE name = 'save generating setting';
UPDATE permissions SET name = 'setting.saveGrading' WHERE name = 'save grading setting';
UPDATE permissions SET name = 'setting.saveVerification' WHERE name = 'save verification setting';
UPDATE permissions SET name = 'setting.getAdvance' WHERE name = 'get advance setting';
UPDATE permissions SET name = 'setting.getGenerating' WHERE name = 'get generating setting';
UPDATE permissions SET name = 'setting.getOAuth2' WHERE name = 'get OAuth2 setting';
UPDATE permissions SET name = 'setting.saveOAuth2' WHERE name = 'save OAuth2 setting';

-- 11. Rename permissions: rateLimit group (SCREAMING_SNAKE to camelCase dot)
UPDATE permissions SET name = 'rateLimit.bypass' WHERE name = 'BYPASS_RATE_LIMIT';
UPDATE permissions SET name = 'rateLimit.viewMonitor' WHERE name = 'VIEW_RATE_LIMIT_MONITOR';
UPDATE permissions SET name = 'rateLimit.viewConfig' WHERE name = 'VIEW_RATE_LIMIT_CONFIG';
UPDATE permissions SET name = 'rateLimit.modifyConfig' WHERE name = 'MODIFY_RATE_LIMIT_CONFIG';

-- 12. Normalize three-segment to two-segment: answerLimit
UPDATE permissions SET name = 'answerLimit.viewSetting' WHERE name = 'answerLimit.view.setting';
UPDATE permissions SET name = 'answerLimit.manageSetting' WHERE name = 'answerLimit.manage.setting';
UPDATE permissions SET name = 'answerLimit.viewCount' WHERE name = 'answerLimit.view.count';
UPDATE permissions SET name = 'answerLimit.viewWhitelist' WHERE name = 'answerLimit.view.whitelist';
UPDATE permissions SET name = 'answerLimit.manageWhitelist' WHERE name = 'answerLimit.manage.whitelist';
UPDATE permissions SET name = 'answerLimit.manageRecord' WHERE name = 'answerLimit.manage.record';

-- 13. Normalize three-segment to two-segment: thirdPartyApi
UPDATE permissions SET name = 'thirdPartyApi.viewSetting' WHERE name = 'thirdPartyApi.view.setting';
UPDATE permissions SET name = 'thirdPartyApi.manageSetting' WHERE name = 'thirdPartyApi.manage.setting';
UPDATE permissions SET name = 'thirdPartyApi.viewWhitelist' WHERE name = 'thirdPartyApi.view.whitelist';
UPDATE permissions SET name = 'thirdPartyApi.manageWhitelist' WHERE name = 'thirdPartyApi.manage.whitelist';

-- 14. Fix V18 English descriptions to Chinese
UPDATE permission_groups SET description = '题目版本管理' WHERE name = 'questionVersion' AND description = 'Question Version Management';
UPDATE permissions SET description = '查看题目版本历史' WHERE name = 'questionVersion.view' AND description = 'View question version history';
UPDATE permissions SET description = '手动触发成绩重算' WHERE name = 'questionVersion.recalculate' AND description = 'Manually trigger score recalculation';
UPDATE permissions SET description = '查看成绩重算日志' WHERE name = 'questionVersion.viewRecalculationLog' AND description = 'View score recalculation logs';
UPDATE permissions SET description = '审批成绩重算' WHERE name = 'questionVersion.approveRecalculation' AND description = 'Approve or reject score recalculation';
