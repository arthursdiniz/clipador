ALTER TABLE clip_candidate
    ADD COLUMN title VARCHAR(160);

UPDATE clip_candidate
SET title = LEFT(COALESCE(NULLIF(BTRIM(hook), ''), NULLIF(BTRIM(source_text), ''),
                          'Momento em destaque'), 160);

ALTER TABLE clip_candidate
    ALTER COLUMN title SET NOT NULL;
