-- ============================================
-- V18: Question version management + Score auto-recalculation + DB optimization
-- ============================================

-- 1. questions table add version management columns (one per ALTER for H2 compat)
ALTER TABLE questions ADD COLUMN version_number INT NOT NULL DEFAULT 1;
ALTER TABLE questions ADD COLUMN version_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE questions ADD COLUMN version_group_id CHAR(36) NULL;
ALTER TABLE questions ADD COLUMN previous_version_id CHAR(36) NULL;

-- Initialize: all existing questions set to v1 active version, version_group_id = self ID
UPDATE questions SET version_group_id = id WHERE version_group_id IS NULL;

-- 2. Question version chain table
CREATE TABLE question_version_chain
(
    id                CHAR(36)     NOT NULL PRIMARY KEY,
    version_group_id  CHAR(36)     NOT NULL,
    from_version_id   CHAR(36)     NOT NULL,
    to_version_id     CHAR(36)     NOT NULL,
    change_type       VARCHAR(20)  NOT NULL,
    change_description TEXT        NULL,
    created_at        DATETIME     NOT NULL,
    created_by_qq     BIGINT       NULL
);

CREATE INDEX idx_qvc_group ON question_version_chain (version_group_id);
CREATE INDEX idx_qvc_from ON question_version_chain (from_version_id);
CREATE INDEX idx_qvc_to ON question_version_chain (to_version_id);
CREATE INDEX idx_qvc_created_at ON question_version_chain (created_at);

-- 3. Score recalculation log table
CREATE TABLE score_recalculation_log
(
    id                       CHAR(36)     NOT NULL PRIMARY KEY,
    question_id              CHAR(36)     NOT NULL,
    trigger_type             VARCHAR(20)  NOT NULL,
    triggered_at             DATETIME     NOT NULL,
    triggered_by_qq          BIGINT       NULL,
    affected_exam_count      INT          NOT NULL DEFAULT 0,
    score_changed_exam_count INT          NOT NULL DEFAULT 0,
    status                   VARCHAR(20)  NOT NULL,
    completed_at             DATETIME     NULL,
    error_message            TEXT         NULL
);

CREATE INDEX idx_srl_question ON score_recalculation_log (question_id);
CREATE INDEX idx_srl_status ON score_recalculation_log (status);
CREATE INDEX idx_srl_triggered_at ON score_recalculation_log (triggered_at);

-- 4. Score change detail table
CREATE TABLE score_change_detail
(
    id                    CHAR(36)     NOT NULL PRIMARY KEY,
    recalculation_log_id  CHAR(36)     NOT NULL,
    exam_data_id          CHAR(36)     NOT NULL,
    old_score             FLOAT        NOT NULL,
    new_score             FLOAT        NOT NULL,
    old_level             VARCHAR(255) NULL,
    new_level             VARCHAR(255) NULL,
    old_level_id          CHAR(36)     NULL,
    new_level_id          CHAR(36)     NULL,
    score_changed         BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_scd_log ON score_change_detail (recalculation_log_id);
CREATE INDEX idx_scd_exam ON score_change_detail (exam_data_id);

-- 5. exam_data table add raw_answer_data column
ALTER TABLE exam_data ADD COLUMN raw_answer_data MEDIUMTEXT NULL;

-- 6. Optimize indexes
CREATE INDEX idx_questions_version_status ON questions (version_status, enabled);
CREATE INDEX idx_questions_version_group ON questions (version_group_id, version_number);
CREATE INDEX idx_exam_data_question_ids ON exam_data_question_ids (question_ids);

-- 7. New permission group
INSERT IGNORE INTO permission_groups (name, description)
VALUES ('questionVersion', 'Question Version Management');

-- 8. New permissions
INSERT IGNORE INTO permissions (id, description, name, group_name)
VALUES ('qv-view-001', 'View question version history', 'questionVersion.view', 'questionVersion'),
       ('qv-manage-001', 'Manually trigger score recalculation', 'questionVersion.recalculate', 'questionVersion'),
       ('qv-view-002', 'View score recalculation logs', 'questionVersion.viewRecalculationLog', 'questionVersion');

-- 9. Grant permissions to roles
INSERT IGNORE INTO role_permission_mapping (role_type, permission_id)
VALUES ('super admin', 'qv-view-001'),
       ('super admin', 'qv-manage-001'),
       ('super admin', 'qv-view-002'),
       ('admin', 'qv-view-001'),
       ('admin', 'qv-view-002');
