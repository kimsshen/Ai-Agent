package com.example.industrialai.iiot;

import com.example.industrialai.model.PushLogStatistics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

@Service
public class PushLogQueryService {

    private static final int DEFAULT_LOOKBACK_DAYS = 7;
    private static final Logger log = LoggerFactory.getLogger(PushLogQueryService.class);

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
        log.info("PUSH_LOG_QUERY requestedChannel={} normalizedChannel={} requestedFrom={} requestedTo={} "
                        + "days={} resolvedFrom={} resolvedTo={}",
                channel, normalizedChannel, from, to, days, resolvedFrom, resolvedTo);
        PushLogQuery query = new PushLogQuery(normalizedChannel, resolvedFrom, resolvedTo);
        PushLogStatistics statistics = provider.findStatistics(query);
        if (normalizedChannel == null || statistics == null || statistics.totalCount() > 0) {
            logResult(statistics, false);
            return statistics;
        }

        String resolvedChannel = resolveUniqueChannel(normalizedChannel);
        if (resolvedChannel.equals(normalizedChannel)) {
            logResult(statistics, false);
            return statistics;
        }
        log.info("Resolved abbreviated push_log channel '{}' to '{}'", normalizedChannel, resolvedChannel);
        PushLogStatistics resolvedStatistics = provider.findStatistics(
                new PushLogQuery(resolvedChannel, resolvedFrom, resolvedTo));
        logResult(resolvedStatistics, true);
        return resolvedStatistics;
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

    private String resolveUniqueChannel(String requestedChannel) {
        List<String> availableChannels = provider.findChannels();
        if (availableChannels == null || availableChannels.isEmpty()) {
            return requestedChannel;
        }
        List<String> matches = availableChannels.stream()
                .filter(candidate -> candidate != null && !candidate.isBlank())
                .map(String::trim)
                .filter(candidate -> candidate.contains(requestedChannel)
                        || requestedChannel.contains(candidate))
                .distinct()
                .toList();
        return matches.size() == 1 ? matches.get(0) : requestedChannel;
    }

    private void logResult(PushLogStatistics statistics, boolean usedChannelFallback) {
        log.info("PUSH_LOG_RESULT channel={} from={} to={} totalCount={} dailyBucketCount={} "
                        + "channelCount={} usedChannelFallback={}",
                statistics == null ? null : statistics.channel(),
                statistics == null ? null : statistics.from(),
                statistics == null ? null : statistics.to(),
                statistics == null ? null : statistics.totalCount(),
                statistics == null || statistics.dailyCounts() == null ? 0 : statistics.dailyCounts().size(),
                statistics == null || statistics.channels() == null ? 0 : statistics.channels().size(),
                usedChannelFallback);
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
