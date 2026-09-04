package com.example.industrialai.iiot;

import com.example.industrialai.model.PushLogStatistics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class PushLogQueryService {

    private static final Duration DEFAULT_LOOKBACK = Duration.ofDays(7);

    private final PushLogDataProvider provider;
    private final Clock clock;

    @Autowired
    public PushLogQueryService(PushLogDataProvider provider) {
        this(provider, Clock.systemUTC());
    }

    PushLogQueryService(PushLogDataProvider provider, Clock clock) {
        this.provider = provider;
        this.clock = clock;
    }

    public PushLogStatistics getStatistics(String channel, Instant from, Instant to) {
        return getStatistics(channel, from, to, null);
    }

    public PushLogStatistics getStatistics(String channel, Instant from, Instant to, Integer days) {
        validateDays(days);
        String normalizedChannel = normalizeChannel(channel);
        Instant resolvedTo = to == null ? clock.instant() : to;
        Instant resolvedFrom = from == null
                ? resolvedTo.minus(resolveLookback(days))
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

    private Duration resolveLookback(Integer days) {
        if (days == null) {
            return DEFAULT_LOOKBACK;
        }
        return Duration.ofDays(days);
    }

    private void validateDays(Integer days) {
        if (days != null && (days < 1 || days > 365)) {
            throw new IllegalArgumentException("days must be between 1 and 365");
        }
    }
}
