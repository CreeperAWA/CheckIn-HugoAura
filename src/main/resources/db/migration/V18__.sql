-- Add unique constraint to users.name to prevent duplicate usernames
ALTER TABLE users ADD CONSTRAINT users_name_unique UNIQUE (name);

-- Fix: remove duplicate usernames by keeping the one with highest QQ number
-- (This is a safety measure; adjust strategy as needed for existing data)
DELETE t1 FROM users t1
INNER JOIN users t2 
WHERE t1.name = t2.name 
  AND t1.qqnumber < t2.qqnumber
  AND t1.name IS NOT NULL;
