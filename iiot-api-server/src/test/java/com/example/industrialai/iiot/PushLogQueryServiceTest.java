package com.example.industrialai.iiot;

import com.example.industrialai.model.PushLogStatistics;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PushLogQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-04T05:00:00Z");

    @Test
    void normalizesChannelAndUsesHalfOpenDefaultWindow() {
        PushLogDataProvider provider = mock(PushLogDataProvider.class);
        PushLogQueryService service = new PushLogQueryService(
                provider, Clock.fixed(NOW, ZoneOffset.UTC), ZoneOffset.UTC);

        service.getStatistics("  assembly-line  ", null, null);

        verify(provider).findStatistics(new PushLogQuery(
                "assembly-line", Instant.parse("2026-08-29T00:00:00Z"), NOW));
    }

    @Test
    void rejectsReversedTimeWindowBeforeCallingProvider() {
        PushLogDataProvider provider = mock(PushLogDataProvider.class);
        PushLogQueryService service = new PushLogQueryService(provider);
        Instant from = Instant.parse("2026-09-05T00:00:00Z");
        Instant to = Instant.parse("2026-09-04T00:00:00Z");

        assertThrows(IllegalArgumentException.class,
                () -> service.getStatistics(null, from, to));
        verifyNoInteractions(provider);
    }

    @Test
    void usesRequestedDaysWhenTimeRangeIsOmitted() {
        PushLogDataProvider provider = mock(PushLogDataProvider.class);
        PushLogQueryService service = new PushLogQueryService(
                provider, Clock.fixed(NOW, ZoneOffset.UTC), ZoneOffset.UTC);

        service.getStatistics("assembly-line", null, null, 3);

        verify(provider).findStatistics(new PushLogQuery(
                "assembly-line", Instant.parse("2026-09-02T00:00:00Z"), NOW));
    }

    @Test
    void rejectsDaysOutsideSupportedRange() {
        PushLogDataProvider provider = mock(PushLogDataProvider.class);
        PushLogQueryService service = new PushLogQueryService(provider,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThrows(IllegalArgumentException.class,
                () -> service.getStatistics("assembly-line", null, null, 0));
        verifyNoInteractions(provider);
    }

    @Test
    void retriesZeroResultWithOneUnambiguousContainingChannel() {
        PushLogDataProvider provider = mock(PushLogDataProvider.class);
        PushLogQueryService service = new PushLogQueryService(
                provider, Clock.fixed(NOW, ZoneOffset.UTC), ZoneOffset.UTC);
        PushLogQuery abbreviatedQuery = new PushLogQuery(
                "扬州组件M2层压机", Instant.parse("2026-08-06T00:00:00Z"), NOW);
        PushLogQuery resolvedQuery = new PushLogQuery(
                "扬州组件M2层压机报警", Instant.parse("2026-08-06T00:00:00Z"), NOW);
        when(provider.findStatistics(abbreviatedQuery)).thenReturn(emptyStatistics(abbreviatedQuery));
        when(provider.findChannels()).thenReturn(List.of(
                "扬州组件M1层压机报警", "扬州组件M2层压机报警"));

        service.getStatistics("扬州组件M2层压机", null, null, 30);

        verify(provider).findStatistics(abbreviatedQuery);
        verify(provider).findStatistics(resolvedQuery);
    }

    @Test
    void keepsRequestedChannelWhenContainingMatchIsAmbiguous() {
        PushLogDataProvider provider = mock(PushLogDataProvider.class);
        PushLogQueryService service = new PushLogQueryService(
                provider, Clock.fixed(NOW, ZoneOffset.UTC), ZoneOffset.UTC);
        PushLogQuery query = new PushLogQuery(
                "扬州组件M2", Instant.parse("2026-08-06T00:00:00Z"), NOW);
        when(provider.findStatistics(query)).thenReturn(emptyStatistics(query));
        when(provider.findChannels()).thenReturn(List.of(
                "扬州组件M2层压机报警", "扬州组件M2串焊机报警推送"));

        service.getStatistics("扬州组件M2", null, null, 30);

        verify(provider).findStatistics(query);
    }

    private PushLogStatistics emptyStatistics(PushLogQuery query) {
        return new PushLogStatistics(
                query.channel(), query.from(), query.to(), 0, List.of(), List.of());
    }
}
