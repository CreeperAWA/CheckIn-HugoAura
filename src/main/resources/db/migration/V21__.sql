-- Add trigger_version_id column to score_recalculation_log table
ALTER TABLE score_recalculation_log ADD COLUMN trigger_version_id CHAR(36);