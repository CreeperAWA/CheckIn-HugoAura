-- Rename robot_tokens table to http_api_tokens
ALTER TABLE robot_tokens RENAME TO http_api_tokens;

-- Remove created_at and updated_at columns from rate_limit_rules table
ALTER TABLE rate_limit_rules DROP COLUMN IF EXISTS created_at;
ALTER TABLE rate_limit_rules DROP COLUMN IF EXISTS updated_at;