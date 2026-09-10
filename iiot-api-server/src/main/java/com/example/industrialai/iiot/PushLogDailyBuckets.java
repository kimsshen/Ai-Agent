package com.example.industrialai.iiot;

import com.example.industrialai.model.PushLogDailyCount;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

final class PushLogDailyBuckets {

    private static final long MAX_FILLED_DAYS = 366;

    private PushLogDailyBuckets() {
    }

    static List<PushLogDailyCount> fillMissingDays(
            Map<LocalDate, Long> observedCounts, Instant from, Instant to, ZoneId timeZone) {
        if (observedCounts.isEmpty()) {
            return List.of();
        }
        LocalDate firstDate = from.atZone(timeZone).toLocalDate();
        ZonedDateTime end = to.atZone(timeZone);
        LocalDate lastDate = end.toLocalDate();
        if (end.toLocalTime().equals(LocalTime.MIDNIGHT)) {
            lastDate = lastDate.minusDays(1);
        }
        long dayCount = ChronoUnit.DAYS.between(firstDate, lastDate) + 1;
        if (dayCount < 1 || dayCount > MAX_FILLED_DAYS) {
            return observedCounts.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> new PushLogDailyCount(entry.getKey(), entry.getValue()))
                    .toList();
        }
        return firstDate.datesUntil(lastDate.plusDays(1))
                .map(date -> new PushLogDailyCount(date, observedCounts.getOrDefault(date, 0L)))
                .toList();
    }
}
