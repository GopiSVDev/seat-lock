package com.seatlock.backend.catalogue.internal.city;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

// Plain SQL: pg_trgm has no JPQL equivalent.
@Repository
@RequiredArgsConstructor
public class CitySearchRepository {

    private static final RowMapper<CityMatch> CITY_MATCH = (rs, rowNum) -> new CityMatch(rs.getLong("id"),
            rs.getString("name"), rs.getString("state"), rs.getObject("popular_rank", Integer.class),
            rs.getString("matched_alias"));

    private final JdbcClient jdbcClient;

    // Prefix or trigram match (% = similarity >= 0.3) on names and aliases; one row per city.
    public List<CityMatch> search(String query, boolean popularOnly, boolean withTheatresOnly, int limit) {
        return jdbcClient.sql("""
                with candidates as (
                    select c.id, null::varchar as matched_alias,
                           similarity(c.name, :query) as score, c.name ilike :prefix as is_prefix
                    from catalogue.city c
                    where c.name % :query or c.name ilike :prefix
                    union all
                    select a.city_id, a.alias,
                           similarity(a.alias, :query), a.alias ilike :prefix
                    from catalogue.city_alias a
                    where a.alias % :query or a.alias ilike :prefix
                ),
                best as (
                    select distinct on (id) id, matched_alias, score, is_prefix
                    from candidates
                    order by id, is_prefix desc, score desc, matched_alias nulls first
                )
                select c.id, c.name, c.state, c.popular_rank, b.matched_alias
                from best b
                join catalogue.city c on c.id = b.id
                where (:popularOnly = false or c.popular_rank is not null)
                  and (:withTheatresOnly = false
                       or exists (select 1 from catalogue.theatre t where t.city_id = c.id))
                order by b.is_prefix desc, b.matched_alias is null desc, b.score desc, c.name
                limit :limit
                """).param("query", query).param("prefix", escapeLike(query) + "%").param("popularOnly", popularOnly)
                .param("withTheatresOnly", withTheatresOnly).param("limit", limit).query(CITY_MATCH).list();
    }

    public List<CityMatch> list(boolean popularOnly, boolean withTheatresOnly) {
        return jdbcClient.sql("""
                select c.id, c.name, c.state, c.popular_rank, null as matched_alias
                from catalogue.city c
                where (:popularOnly = false or c.popular_rank is not null)
                  and (:withTheatresOnly = false
                       or exists (select 1 from catalogue.theatre t where t.city_id = c.id))
                order by case when :popularOnly then c.popular_rank end, c.name
                """).param("popularOnly", popularOnly).param("withTheatresOnly", withTheatresOnly).query(CITY_MATCH)
                .list();
    }

    // Escape LIKE wildcards in user text
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
