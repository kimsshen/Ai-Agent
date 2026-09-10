package com.example.industrialai.iiot;

import com.example.industrialai.model.PushLogDailyCount;
import com.example.industrialai.model.PushLogStatistics;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InMemoryPushLogDataProviderTest {

    private static final Instant NOW = Instant.parse("2026-09-09T12:00:00Z");

    @Test
    void returnsChronologicalDailyCountsThatMatchTheTotal() {
        InMemoryPushLogDataProvider provider = new InMemoryPushLogDataProvider(
                Clock.fixed(NOW, ZoneOffset.UTC), ZoneId.of("Asia/Shanghai"));

        PushLogStatistics statistics = provider.findStatistics(new PushLogQuery(
                null, NOW.minusSeconds(2 * 24 * 60 * 60), NOW.plusSeconds(1)));

        assertEquals(5, statistics.totalCount());
        assertEquals(5, statistics.dailyCounts().stream().mapToLong(PushLogDailyCount::count).sum());
        assertEquals(3, statistics.dailyCounts().size());
        assertEquals(LocalDate.of(2026, 9, 7), statistics.dailyCounts().get(0).date());
        assertEquals(0, statistics.dailyCounts().get(0).count());
        assertEquals(LocalDate.of(2026, 9, 8), statistics.dailyCounts().get(1).date());
        assertEquals(1, statistics.dailyCounts().get(1).count());
        assertEquals(LocalDate.of(2026, 9, 9), statistics.dailyCounts().get(2).date());
        assertEquals(4, statistics.dailyCounts().get(2).count());
    }

    @Test
    void dailyCountsHonorTheChannelFilter() {
        InMemoryPushLogDataProvider provider = new InMemoryPushLogDataProvider(
                Clock.fixed(NOW, ZoneOffset.UTC), ZoneId.of("Asia/Shanghai"));

        PushLogStatistics statistics = provider.findStatistics(new PushLogQuery(
                "装配一线", NOW.minusSeconds(24 * 60 * 60), NOW.plusSeconds(1)));

        assertEquals(3, statistics.totalCount());
        assertEquals(2, statistics.dailyCounts().size());
        assertEquals(0, statistics.dailyCounts().get(0).count());
        assertEquals(3, statistics.dailyCounts().get(1).count());
    }
}
