package com.example.industrialai.iiot;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PushLogQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-04T05:00:00Z");

    @Test
    void normalizesChannelAndUsesHalfOpenDefaultWindow() {
        PushLogDataProvider provider = mock(PushLogDataProvider.class);
        PushLogQueryService service = new PushLogQueryService(
                provider, Clock.fixed(NOW, ZoneOffset.UTC));

        service.getStatistics("  assembly-line  ", null, null);

        verify(provider).findStatistics(new PushLogQuery(
                "assembly-line", NOW.minusSeconds(7 * 24 * 60 * 60), NOW));
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
                provider, Clock.fixed(NOW, ZoneOffset.UTC));

        service.getStatistics("assembly-line", null, null, 3);

        verify(provider).findStatistics(new PushLogQuery(
                "assembly-line", NOW.minusSeconds(3 * 24 * 60 * 60), NOW));
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
}
