-- Change version_number from INT to VARCHAR(7) for SHA1-based version numbers
ALTER TABLE questions MODIFY COLUMN version_number VARCHAR(7) NOT NULL DEFAULT '1';
