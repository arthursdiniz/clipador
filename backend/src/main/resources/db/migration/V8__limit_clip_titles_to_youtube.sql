UPDATE clip_candidate
SET title = LEFT(title, 100)
WHERE CHAR_LENGTH(title) > 100;

ALTER TABLE clip_candidate
    ALTER COLUMN title TYPE VARCHAR(100);
