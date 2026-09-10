package com.example.industrialai.iiot;

import com.example.industrialai.model.PushLogStatistics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

@Service
public class PushLogQueryService {

    private static final int DEFAULT_LOOKBACK_DAYS = 7;

    private final PushLogDataProvider provider;
    private final Clock clock;
    private final ZoneId timeZone;

    @Autowired
    public PushLogQueryService(
            PushLogDataProvider provider,
            @Value("${iiot.push-log.time-zone:Asia/Shanghai}") String timeZone) {
        this(provider, Clock.systemUTC(), ZoneId.of(timeZone));
    }

    PushLogQueryService(PushLogDataProvider provider) {
        this(provider, Clock.systemUTC(), ZoneId.of("Asia/Shanghai"));
    }

    PushLogQueryService(PushLogDataProvider provider, Clock clock) {
        this(provider, clock, ZoneId.of("Asia/Shanghai"));
    }

    PushLogQueryService(PushLogDataProvider provider, Clock clock, ZoneId timeZone) {
        this.provider = provider;
        this.clock = clock;
        this.timeZone = timeZone;
    }

    public PushLogStatistics getStatistics(String channel, Instant from, Instant to) {
        return getStatistics(channel, from, to, null);
    }

    public PushLogStatistics getStatistics(String channel, Instant from, Instant to, Integer days) {
        validateDays(days);
        String normalizedChannel = normalizeChannel(channel);
        Instant resolvedTo = to == null ? clock.instant() : to;
        Instant resolvedFrom = from == null
                ? resolveFrom(resolvedTo, days)
                : from;
        if (!resolvedFrom.isBefore(resolvedTo)) {
            throw new IllegalArgumentException("from must be earlier than to");
        }
        return provider.findStatistics(new PushLogQuery(normalizedChannel, resolvedFrom, resolvedTo));
    }

    public List<String> getChannels() {
        return provider.findChannels();
    }

    private String normalizeChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return null;
        }
        return channel.trim();
    }

    private Instant resolveFrom(Instant to, Integer days) {
        int lookbackDays = days == null ? DEFAULT_LOOKBACK_DAYS : days;
        return to.atZone(timeZone).toLocalDate()
                .minusDays(lookbackDays - 1L)
                .atStartOfDay(timeZone)
                .toInstant();
    }

    private void validateDays(Integer days) {
        if (days != null && (days < 1 || days > 365)) {
            throw new IllegalArgumentException("days must be between 1 and 365");
        }
    }
}
