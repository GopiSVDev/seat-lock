package com.seatlock.backend.catalogue.internal.theatre;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class TheatreBrowseRepository {

    private static final String FILTERED = """
            from catalogue.theatre t
            join catalogue.city c on c.id = t.city_id
            where (cast(:cityId as bigint) is null or t.city_id = :cityId)
              and (cast(:pattern as text) is null or t.name ilike :pattern)
            """;

    private final JdbcClient jdbcClient;

    // lower(): byte collation would sort "AGS" before "Abirami"
    public List<TheatreRow> find(Long cityId, String nameContains, int limit, long offset) {
        return jdbcClient
                .sql("select t.id, t.name, t.address, c.id as city_id, c.name as city_name " + FILTERED
                        + " order by lower(t.name), t.id limit :limit offset :offset")
                .param("cityId", cityId).param("pattern", pattern(nameContains)).param("limit", limit)
                .param("offset", offset).query((rs, rowNum) -> new TheatreRow(rs.getLong("id"), rs.getString("name"),
                        rs.getString("address"), rs.getLong("city_id"), rs.getString("city_name")))
                .list();
    }

    public long count(Long cityId, String nameContains) {
        return jdbcClient.sql("select count(*) " + FILTERED).param("cityId", cityId)
                .param("pattern", pattern(nameContains)).query(Long.class).single();
    }

    // Escape LIKE wildcards in user text
    private static String pattern(String nameContains) {
        if (nameContains == null) {
            return null;
        }
        return "%" + nameContains.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
