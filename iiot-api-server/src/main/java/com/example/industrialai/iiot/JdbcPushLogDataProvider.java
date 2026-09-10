package com.example.industrialai.iiot;

import com.example.industrialai.model.PushLogAlarmItem;
import com.example.industrialai.model.PushLogChannelStatistics;
import com.example.industrialai.model.PushLogDailyCount;
import com.example.industrialai.model.PushLogStatistics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** PostgreSQL read-only access for the push_log alarm table. */
@Component
@ConditionalOnProperty(name = "iiot.provider.type", havingValue = "jdbc")
public class JdbcPushLogDataProvider implements PushLogDataProvider {

    private final JdbcTemplate jdbcTemplate;
    private final ZoneId timeZone;

    public JdbcPushLogDataProvider(
            JdbcTemplate jdbcTemplate,
            @Value("${iiot.push-log.time-zone:Asia/Shanghai}") String timeZone) {
        this.jdbcTemplate = jdbcTemplate;
        this.timeZone = ZoneId.of(timeZone);
    }

    @Override
    public PushLogStatistics findStatistics(PushLogQuery query) {
        String channel = query.channel();
        Instant from = query.from();
        Instant to = query.to();
        StringBuilder sql = new StringBuilder("""
                SELECT channel,
                       COALESCE(exception_name, '') AS exception_name,
                       COALESCE(msg, '') AS msg,
                       COUNT(*) AS alarm_count,
                       MIN(creation_date) AS first_creation_date,
                       MAX(creation_date) AS last_creation_date
                FROM push_log
                WHERE CAST(creation_date AS TIMESTAMP) >= CAST(? AS TIMESTAMP)
                  AND CAST(creation_date AS TIMESTAMP) < CAST(? AS TIMESTAMP)
                """);
        List<Object> args = new ArrayList<>(List.of(formatBoundary(from), formatBoundary(to)));
        if (channel != null) {
            sql.append(" AND channel = ? ");
            args.add(channel);
        }
        sql.append("""
                GROUP BY channel, COALESCE(exception_name, ''), COALESCE(msg, '')
                ORDER BY channel NULLS LAST, alarm_count DESC, exception_name, msg
                """);

        Map<String, List<PushLogAlarmItem>> grouped = new LinkedHashMap<>();
        jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            grouped.computeIfAbsent(rs.getString("channel"), ignored -> new ArrayList<>())
                    .add(new PushLogAlarmItem(
                            rs.getString("exception_name"),
                            rs.getString("msg"),
                            rs.getLong("alarm_count"),
                            toInstant(rs.getString("first_creation_date")),
                            toInstant(rs.getString("last_creation_date"))));
            return null;
        }, args.toArray());

        List<PushLogChannelStatistics> channels = grouped.entrySet().stream()
                .map(entry -> new PushLogChannelStatistics(
                        entry.getKey(),
                        entry.getValue().stream().mapToLong(PushLogAlarmItem::count).sum(),
                        entry.getValue()))
                .toList();
        List<PushLogDailyCount> dailyCounts = findDailyCounts(query);
        return new PushLogStatistics(
                channel, from, to,
                channels.stream().mapToLong(PushLogChannelStatistics::totalCount).sum(),
                dailyCounts,
                channels);
    }

    private List<PushLogDailyCount> findDailyCounts(PushLogQuery query) {
        StringBuilder sql = new StringBuilder("""
                SELECT CAST(CAST(creation_date AS TIMESTAMP) AS DATE) AS alarm_date,
                       COUNT(*) AS alarm_count
                FROM push_log
                WHERE CAST(creation_date AS TIMESTAMP) >= CAST(? AS TIMESTAMP)
                  AND CAST(creation_date AS TIMESTAMP) < CAST(? AS TIMESTAMP)
                """);
        List<Object> args = new ArrayList<>(List.of(
                formatBoundary(query.from()), formatBoundary(query.to())));
        if (query.channel() != null) {
            sql.append(" AND channel = ? ");
            args.add(query.channel());
        }
        sql.append("""
                GROUP BY CAST(CAST(creation_date AS TIMESTAMP) AS DATE)
                ORDER BY alarm_date
                """);
        List<PushLogDailyCount> observed = jdbcTemplate.query(
                sql.toString(), (rs, rowNum) -> new PushLogDailyCount(
                rs.getDate("alarm_date").toLocalDate(),
                rs.getLong("alarm_count")), args.toArray());
        Map<LocalDate, Long> observedCounts = observed.stream().collect(Collectors.toMap(
                PushLogDailyCount::date,
                PushLogDailyCount::count,
                Long::sum,
                TreeMap::new));
        return PushLogDailyBuckets.fillMissingDays(
                observedCounts, query.from(), query.to(), timeZone);
    }

    @Override
    public List<String> findChannels() {
        return jdbcTemplate.query("""
                SELECT DISTINCT channel
                FROM push_log
                WHERE channel IS NOT NULL AND channel <> ''
                ORDER BY channel
                """, (rs, rowNum) -> rs.getString("channel"));
    }

    private String formatBoundary(Instant value) {
        return Timestamp.valueOf(value.atZone(timeZone).toLocalDateTime()).toString();
    }

    private Instant toInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Timestamp.valueOf(value.trim().replace('T', ' ')).toLocalDateTime()
                    .atZone(timeZone)
                    .toInstant();
        } catch (IllegalArgumentException ignored) {
            try {
                return Instant.parse(value.trim());
            } catch (DateTimeParseException exception) {
                throw new IllegalArgumentException(
                        "push_log.creation_date has unsupported value: " + value, exception);
            }
        }
    }
}
