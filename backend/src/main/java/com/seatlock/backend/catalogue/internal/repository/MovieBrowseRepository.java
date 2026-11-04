package com.seatlock.backend.catalogue.internal.repository;

import com.seatlock.backend.catalogue.internal.domain.Certification;
import com.seatlock.backend.catalogue.internal.domain.Genre;
import com.seatlock.backend.catalogue.internal.domain.Language;
import com.seatlock.backend.catalogue.internal.domain.ShowFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

// Movie lists in plain SQL: per-movie aggregates over shows (count, languages, formats) and array filters.
// Filters arrive as arrays; an empty array means "no filter".
@Repository
@RequiredArgsConstructor
public class MovieBrowseRepository {

    private static final String CARD_COLUMNS = """
            m.id, m.title, m.poster_url, m.genres, m.certification, m.duration_minutes, m.release_date""";

    // Now showing in a city: movies with at least one show that hasn't started, in a theatre of that city.
    // Languages, formats and the show count cover all of the city's upcoming shows, so a card lists everything
    // on offer and the order doesn't change with the filters. Filters only pick which movies appear:
    // values of one filter are OR-ed (Tamil or English), different filters AND-ed.
    private static final String NOW_SHOWING = """
            with city_shows as (
                select s.movie_id,
                       count(*) as show_count,
                       array_agg(distinct s.language order by s.language) as languages,
                       array_agg(distinct s.format order by s.format) as formats,
                       bool_or(s.language = any(:languages)) as has_language,
                       bool_or(s.format = any(:formats)) as has_format
                from catalogue.shows s
                join catalogue.screen sc on sc.id = s.screen_id
                join catalogue.theatre t on t.id = sc.theatre_id
                where t.city_id = :cityId
                  and s.start_time > :now
                group by s.movie_id
            )
            select %s
            from city_shows cs
            join catalogue.movie m on m.id = cs.movie_id
            where (cardinality(:languages) = 0 or cs.has_language)
              and (cardinality(:formats) = 0 or cs.has_format)
              and (cardinality(:genres) = 0 or m.genres && cast(:genres as varchar[]))
            """;

    private static final String CATALOGUE = """
            select %s
            from catalogue.movie m
            where cardinality(:genres) = 0 or m.genres && cast(:genres as varchar[])
            """;

    private static final RowMapper<MovieCard> MOVIE_CARD = (rs, rowNum) -> new MovieCard(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getString("poster_url"),
            toList(rs.getArray("genres"), Genre::valueOf),
            Certification.valueOf(rs.getString("certification")),
            rs.getInt("duration_minutes"),
            rs.getObject("release_date", LocalDate.class),
            toList(rs.getArray("languages"), Language::valueOf),
            toList(rs.getArray("formats"), ShowFormat::valueOf));

    private final JdbcClient jdbcClient;

    // Most upcoming shows first (theatres give the most shows to the films people want), then id so pages
    // never overlap when counts tie
    public List<MovieCard> findNowShowing(MovieFilter filter, Instant now, int limit, long offset) {
        String sql = NOW_SHOWING.formatted(CARD_COLUMNS + ", cs.languages, cs.formats")
                + " order by cs.show_count desc, m.id limit :limit offset :offset";
        return nowShowingQuery(sql, filter, now)
                .param("limit", limit)
                .param("offset", offset)
                .query(MOVIE_CARD)
                .list();
    }

    public long countNowShowing(MovieFilter filter, Instant now) {
        return nowShowingQuery(NOW_SHOWING.formatted("count(*)"), filter, now).query(Long.class).single();
    }

    // Whole catalogue (admin: pick a movie to schedule), newest release first
    public List<MovieCard> findAll(MovieFilter filter, int limit, long offset) {
        String sql = CATALOGUE.formatted(CARD_COLUMNS + ", null::varchar[] as languages, null::varchar[] as formats")
                + " order by m.release_date desc, m.id limit :limit offset :offset";
        return jdbcClient.sql(sql)
                .param("genres", names(filter.genres()))
                .param("limit", limit)
                .param("offset", offset)
                .query(MOVIE_CARD)
                .list();
    }

    public long countAll(MovieFilter filter) {
        return jdbcClient.sql(CATALOGUE.formatted("count(*)"))
                .param("genres", names(filter.genres()))
                .query(Long.class)
                .single();
    }

    private JdbcClient.StatementSpec nowShowingQuery(String sql, MovieFilter filter, Instant now) {
        return jdbcClient.sql(sql)
                .param("cityId", filter.cityId())
                .param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .param("languages", names(filter.languages()))
                .param("formats", names(filter.formats()))
                .param("genres", names(filter.genres()));
    }

    // A Java array (not a List) binds as one Postgres array parameter; a List would expand into "?, ?, ?"
    private static String[] names(List<? extends Enum<?>> values) {
        return values.stream().map(Enum::name).toArray(String[]::new);
    }

    private static <T> List<T> toList(Array array, Function<String, T> convert) throws SQLException {
        return array == null ? null : Arrays.stream((String[]) array.getArray()).map(convert).toList();
    }
}
