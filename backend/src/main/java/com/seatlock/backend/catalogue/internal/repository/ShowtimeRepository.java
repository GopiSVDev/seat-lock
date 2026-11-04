package com.seatlock.backend.catalogue.internal.repository;

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

// Upcoming shows of a movie in a city, for the "pick a theatre and time" page. Plain SQL: a 4-table join
// read straight into rows, and dates computed in India's time zone by Postgres.
// Filters arrive as arrays; an empty array means "no filter".
@Repository
@RequiredArgsConstructor
public class ShowtimeRepository {

    private static final String UPCOMING_IN_CITY = """
            from catalogue.shows s
            join catalogue.screen sc on sc.id = s.screen_id
            join catalogue.theatre t on t.id = sc.theatre_id
            where s.movie_id = :movieId
              and t.city_id = :cityId
              and s.start_time > :now
              and (cardinality(:languages) = 0 or s.language = any(:languages))
              and (cardinality(:formats) = 0 or s.format = any(:formats))
            """;

    private final JdbcClient jdbcClient;

    // Shows starting in [from, to), theatres by name, shows by time
    public List<ShowtimeRow> findShowtimes(Long movieId, Long cityId, List<Language> languages,
                                           List<ShowFormat> formats, Instant now, Instant from, Instant to) {
        return upcoming("""
                        select t.id as theatre_id, t.name as theatre_name, t.address as theatre_address,
                               s.id as show_id, s.start_time, s.language, s.format, sc.name as screen_name
                        """ + UPCOMING_IN_CITY + """
                          and s.start_time >= :from
                          and s.start_time < :to
                        order by t.name, t.id, s.start_time, s.id
                        """, movieId, cityId, languages, formats, now)
                .param("from", utc(from))
                .param("to", utc(to))
                .query((rs, rowNum) -> new ShowtimeRow(
                        rs.getLong("theatre_id"),
                        rs.getString("theatre_name"),
                        rs.getString("theatre_address"),
                        rs.getLong("show_id"),
                        rs.getObject("start_time", OffsetDateTime.class).toInstant(),
                        Language.valueOf(rs.getString("language")),
                        ShowFormat.valueOf(rs.getString("format")),
                        rs.getString("screen_name")))
                .list();
    }

    // The next dates (in India) that have at least one upcoming show: the date strip at the top of the page
    public List<LocalDate> findDates(Long movieId, Long cityId, List<Language> languages, List<ShowFormat> formats,
                                     Instant now, int limit) {
        return upcoming("""
                        select distinct (s.start_time at time zone :zone)::date as show_date
                        """ + UPCOMING_IN_CITY + """
                        order by show_date
                        limit :limit
                        """, movieId, cityId, languages, formats, now)
                .param("zone", IndiaTime.ZONE.getId())
                .param("limit", limit)
                .query((rs, rowNum) -> rs.getObject("show_date", LocalDate.class))
                .list();
    }

    private JdbcClient.StatementSpec upcoming(String sql, Long movieId, Long cityId, List<Language> languages,
                                              List<ShowFormat> formats, Instant now) {
        return jdbcClient.sql(sql)
                .param("movieId", movieId)
                .param("cityId", cityId)
                .param("now", utc(now))
                .param("languages", languages.stream().map(Enum::name).toArray(String[]::new))
                .param("formats", formats.stream().map(Enum::name).toArray(String[]::new));
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
