package com.example.industrialai.mcp;

import com.example.industrialai.model.PushLogStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

/** HTTP boundary for the push_log part of the IIoT API. */
@Component
public class PushLogApiClient {

    private static final Logger log = LoggerFactory.getLogger(PushLogApiClient.class);
    private static final ParameterizedTypeReference<List<String>> CHANNELS_TYPE =
            new ParameterizedTypeReference<>() { };

    private final RestClient iiotClient;

    public PushLogApiClient(RestClient iiotRestClient) {
        this.iiotClient = iiotRestClient;
    }

    public List<String> findChannels() {
        String traceId = UUID.randomUUID().toString();
        log.info("Calling IIoT push_log channels API: traceId={}", traceId);
        return iiotClient.get()
                .uri("/api/iiot/push-logs/channels")
                .header("X-Trace-Id", traceId)
                .retrieve()
                .body(CHANNELS_TYPE);
    }

    public PushLogStatistics findStatistics(String channel, String from, String to, Integer days) {
        String normalizedFrom = normalizeInstant(from, "from");
        String normalizedTo = normalizeInstant(to, "to");
        String traceId = UUID.randomUUID().toString();
        log.info("Calling IIoT push_log statistics API: traceId={}, channel={}, from={}, to={}, days={}",
                traceId, channel, normalizedFrom, normalizedTo, days);

        return iiotClient.get()
                .uri(builder -> {
                    var uriBuilder = builder.path("/api/iiot/push-logs/statistics");
                    if (channel != null && !channel.isBlank()) {
                        uriBuilder.queryParam("channel", channel.trim());
                    }
                    if (normalizedFrom != null) {
                        uriBuilder.queryParam("from", normalizedFrom);
                    }
                    if (normalizedTo != null) {
                        uriBuilder.queryParam("to", normalizedTo);
                    }
                    if (days != null) {
                        uriBuilder.queryParam("days", days);
                    }
                    return uriBuilder.build();
                })
                .header("X-Trace-Id", traceId)
                .retrieve()
                .body(PushLogStatistics.class);
    }

    private String normalizeInstant(String value, String parameter) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return Instant.parse(trimmed).toString();
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(trimmed).toInstant().toString();
            } catch (DateTimeParseException exception) {
                throw new IllegalArgumentException(parameter + " must be a valid ISO-8601 timestamp", exception);
            }
        }
    }
}
