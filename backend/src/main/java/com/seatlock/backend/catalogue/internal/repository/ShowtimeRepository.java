package com.seatlock.backend.catalogue.internal.repository;

import com.seatlock.backend.catalogue.internal.domain.Certification;
import com.seatlock.backend.catalogue.internal.domain.Language;
import com.seatlock.backend.catalogue.internal.domain.ShowFormat;
import com.seatlock.backend.shared.time.IndiaTime;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

// Empty filter arrays and null scope ids mean "any".
@Repository
@RequiredArgsConstructor
public class ShowtimeRepository {

    private static final String UPCOMING = """
            from catalogue.shows s
            join catalogue.screen sc on sc.id = s.screen_id
            join catalogue.theatre t on t.id = sc.theatre_id
            join catalogue.movie m on m.id = s.movie_id
            where (cast(:movieId as bigint) is null or s.movie_id = :movieId)
              and (cast(:cityId as bigint) is null or t.city_id = :cityId)
              and (cast(:theatreId as bigint) is null or t.id = :theatreId)
              and s.start_time > :now
              and (cardinality(:languages) = 0 or s.language = any(:languages))
              and (cardinality(:formats) = 0 or s.format = any(:formats))
            """;

    private final JdbcClient jdbcClient;

    public List<ShowtimeRow> findShowtimes(ShowScope scope, List<Language> languages, List<ShowFormat> formats,
                                           Instant now, Instant from, Instant to) {
        // lower(): byte collation would sort "AGS" before "Abirami"
        String order = scope.groupsByTheatre()
                ? "lower(t.name), t.id, s.start_time, s.id"
                : "lower(m.title), m.id, s.start_time, s.id";
        return upcoming("""
                        select t.id as theatre_id, t.name as theatre_name, t.address as theatre_address,
                               m.id as movie_id, m.title, m.poster_url, m.certification, m.duration_minutes,
                               s.id as show_id, s.start_time, s.language, s.format, sc.name as screen_name
                        """ + UPCOMING + """
                          and s.start_time >= :from
                          and s.start_time < :to
                        order by\s""" + order, scope, languages, formats, now)
                .param("from", utc(from))
                .param("to", utc(to))
                .query((rs, rowNum) -> new ShowtimeRow(
                        rs.getLong("theatre_id"),
                        rs.getString("theatre_name"),
                        rs.getString("theatre_address"),
                        rs.getLong("movie_id"),
                        rs.getString("title"),
                        rs.getString("poster_url"),
                        Certification.valueOf(rs.getString("certification")),
                        rs.getInt("duration_minutes"),
                        rs.getLong("show_id"),
                        rs.getObject("start_time", OffsetDateTime.class).toInstant(),
                        Language.valueOf(rs.getString("language")),
                        ShowFormat.valueOf(rs.getString("format")),
                        rs.getString("screen_name")))
                .list();
    }

    public List<LocalDate> findDates(ShowScope scope, List<Language> languages, List<ShowFormat> formats,
                                     Instant now, int limit) {
        return upcoming("""
                        select distinct (s.start_time at time zone :zone)::date as show_date
                        """ + UPCOMING + """
                        order by show_date
                        limit :limit
                        """, scope, languages, formats, now)
                .param("zone", IndiaTime.ZONE.getId())
                .param("limit", limit)
                .query((rs, rowNum) -> rs.getObject("show_date", LocalDate.class))
                .list();
    }

    private JdbcClient.StatementSpec upcoming(String sql, ShowScope scope, List<Language> languages,
                                              List<ShowFormat> formats, Instant now) {
        return jdbcClient.sql(sql)
                .param("movieId", scope.movieId())
                .param("cityId", scope.cityId())
                .param("theatreId", scope.theatreId())
                .param("now", utc(now))
                .param("languages", languages.stream().map(Enum::name).toArray(String[]::new))
                .param("formats", formats.stream().map(Enum::name).toArray(String[]::new));
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
