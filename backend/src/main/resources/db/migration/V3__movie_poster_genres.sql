-- What a movie card shows besides the title: a poster image and genres.

-- A link to the image (CDN / object storage), never the image itself
ALTER TABLE catalogue.movie ADD COLUMN poster_url varchar(500);

-- Genres from a fixed list (Genre enum), so a filter on "THRILLER" can't miss a movie tagged "Thriler".
-- Existing movies start with none. Adding a genre = enum value + a migration replacing this constraint.
ALTER TABLE catalogue.movie
    ADD COLUMN genres varchar(255)[] NOT NULL DEFAULT '{}',
    ADD CONSTRAINT ck_movie_genres CHECK (genres <@ ARRAY [
        'ACTION', 'ADVENTURE', 'ANIMATION', 'BIOGRAPHY', 'COMEDY', 'CRIME', 'DOCUMENTARY', 'DRAMA', 'FAMILY',
        'FANTASY', 'HISTORICAL', 'HORROR', 'MUSICAL', 'MYSTERY', 'ROMANCE', 'SCI_FI', 'SPORTS', 'THRILLER', 'WAR'
        ]::varchar(255)[]);
