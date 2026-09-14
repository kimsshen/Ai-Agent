package com.example.industrialai.iiot.mcp;

import com.example.industrialai.iiot.PushLogQueryService;
import com.example.industrialai.model.PushLogStatistics;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PushLogReadOnlyToolsTest {

    @Test
    void callsBusinessServiceDirectlyAndNormalizesOffsetTimestamps() {
        PushLogQueryService queryService = mock(PushLogQueryService.class);
        PushLogReadOnlyTools tools = new PushLogReadOnlyTools(queryService);
        Instant from = Instant.parse("2026-09-01T00:00:00Z");
        Instant to = Instant.parse("2026-09-04T00:00:00Z");
        PushLogStatistics expected = new PushLogStatistics(
                "装配一线", from, to, 12, List.of(), List.of());
        when(queryService.getStatistics("装配一线", from, to, 3)).thenReturn(expected);

        PushLogStatistics actual = tools.getPushLogAlarmStatistics(
                "装配一线", "2026-09-01T08:00:00+08:00", "2026-09-04T08:00:00+08:00", 3);

        assertSame(expected, actual);
        verify(queryService).getStatistics("装配一线", from, to, 3);
    }

    @Test
    void exposesChannelsFromTheSameBusinessService() {
        PushLogQueryService queryService = mock(PushLogQueryService.class);
        PushLogReadOnlyTools tools = new PushLogReadOnlyTools(queryService);
        List<String> expected = List.of("装配一线", "包装工序");
        when(queryService.getChannels()).thenReturn(expected);

        List<String> actual = tools.listPushLogChannels();

        assertSame(expected, actual);
        verify(queryService).getChannels();
    }

    @Test
    void rejectsInvalidTimestampBeforeCallingBusinessService() {
        PushLogQueryService queryService = mock(PushLogQueryService.class);
        PushLogReadOnlyTools tools = new PushLogReadOnlyTools(queryService);

        assertThrows(IllegalArgumentException.class,
                () -> tools.getPushLogAlarmStatistics("装配一线", "not-a-time", null, 3));
        verifyNoInteractions(queryService);
    }
}
