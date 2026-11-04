-- Show language becomes a fixed list (Language enum) instead of free text: "English" and "english" were
-- two languages on a movie card and in the language filter.

-- Existing values to enum names: "english", " English " → ENGLISH
UPDATE catalogue.shows SET language = upper(trim(language)) WHERE language <> upper(trim(language));

-- Anything still unknown needs a human decision; fail instead of guessing
DO $$
DECLARE
    unknown text;
BEGIN
    SELECT string_agg(DISTINCT language, ', ') INTO unknown
    FROM catalogue.shows
    WHERE language NOT IN ('ENGLISH', 'HINDI', 'TAMIL', 'TELUGU', 'MALAYALAM', 'KANNADA', 'BENGALI', 'MARATHI',
                           'GUJARATI', 'PUNJABI', 'ODIA', 'ASSAMESE', 'BHOJPURI', 'URDU',
                           'JAPANESE', 'KOREAN', 'FRENCH', 'SPANISH');
    IF unknown IS NOT NULL THEN
        RAISE EXCEPTION 'Shows with unknown languages: %. Map them to a Language value first.', unknown;
    END IF;
END $$;

-- Kept in sync with the Language enum (a new value needs a migration replacing this constraint)
ALTER TABLE catalogue.shows
    ADD CONSTRAINT ck_shows_language CHECK (language IN (
        'ENGLISH', 'HINDI', 'TAMIL', 'TELUGU', 'MALAYALAM', 'KANNADA', 'BENGALI', 'MARATHI',
        'GUJARATI', 'PUNJABI', 'ODIA', 'ASSAMESE', 'BHOJPURI', 'URDU',
        'JAPANESE', 'KOREAN', 'FRENCH', 'SPANISH'));
