-- ============================================
-- V19: Score recalculation approval workflow
-- ============================================

-- 1. Add approval/rejection tracking columns
ALTER TABLE score_recalculation_log ADD COLUMN approved_by_qq BIGINT NULL;
ALTER TABLE score_recalculation_log ADD COLUMN approved_at DATETIME NULL;
ALTER TABLE score_recalculation_log ADD COLUMN rejected_by_qq BIGINT NULL;
ALTER TABLE score_recalculation_log ADD COLUMN rejected_at DATETIME NULL;
ALTER TABLE score_recalculation_log ADD COLUMN question_content_preview VARCHAR(255) NULL;

-- 2. New permission for approving recalculation
INSERT IGNORE INTO permissions (id, description, name, group_name)
VALUES ('qv-manage-002', 'Approve or reject score recalculation', 'questionVersion.approveRecalculation', 'questionVersion');

-- 3. Grant permission to super admin
INSERT IGNORE INTO role_permission_mapping (role_type, permission_id)
VALUES ('super admin', 'qv-manage-002');
