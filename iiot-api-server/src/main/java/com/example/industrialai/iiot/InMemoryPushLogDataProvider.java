package com.example.industrialai.iiot;

import com.example.industrialai.model.PushLogAlarmItem;
import com.example.industrialai.model.PushLogChannelStatistics;
import com.example.industrialai.model.PushLogStatistics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Local sample provider for API and MCP integration testing. */
@Component
@ConditionalOnProperty(name = "iiot.provider.type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryPushLogDataProvider implements PushLogDataProvider {

    private final Instant now = Instant.now();
    private final List<PushLogEntry> entries = List.of(
            new PushLogEntry("装配一线", "温度超限", "焊接头温度超过85℃", now.minus(1, ChronoUnit.HOURS)),
            new PushLogEntry("装配一线", "温度超限", "焊接头温度超过85℃", now.minus(2, ChronoUnit.HOURS)),
            new PushLogEntry("装配一线", "压力异常", "气源压力低于设定值", now.minus(3, ChronoUnit.HOURS)),
            new PushLogEntry("包装工序", "设备离线", "包装机PLC通信中断", now.minus(4, ChronoUnit.HOURS)),
            new PushLogEntry("包装工序", "设备离线", "包装机PLC通信中断", now.minus(1, ChronoUnit.DAYS)));

    @Override
    public PushLogStatistics findStatistics(PushLogQuery query) {
        String channel = query.channel();
        Instant from = query.from();
        Instant to = query.to();
        Map<String, List<PushLogEntry>> grouped = new LinkedHashMap<>();
        entries.stream()
                .filter(entry -> channel == null || entry.channel().equals(channel))
                .filter(entry -> !entry.createdDate().isBefore(from) && entry.createdDate().isBefore(to))
                .forEach(entry -> grouped.computeIfAbsent(entry.channel(), ignored -> new ArrayList<>()).add(entry));

        List<PushLogChannelStatistics> channels = grouped.entrySet().stream()
                .map(entry -> new PushLogChannelStatistics(
                        entry.getKey(),
                        entry.getValue().size(),
                        aggregate(entry.getValue())))
                .toList();

        return new PushLogStatistics(
                channel, from, to,
                channels.stream().mapToLong(PushLogChannelStatistics::totalCount).sum(),
                channels);
    }

    @Override
    public List<String> findChannels() {
        return entries.stream().map(PushLogEntry::channel).distinct().sorted().toList();
    }

    private List<PushLogAlarmItem> aggregate(List<PushLogEntry> entries) {
        return entries.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        entry -> entry.exceptionName() + "\u0000" + entry.message(),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()))
                .values().stream()
                .map(group -> new PushLogAlarmItem(
                        group.get(0).exceptionName(),
                        group.get(0).message(),
                        group.size(),
                        group.stream().map(PushLogEntry::createdDate).min(Comparator.naturalOrder()).orElse(null),
                        group.stream().map(PushLogEntry::createdDate).max(Comparator.naturalOrder()).orElse(null)))
                .sorted(Comparator.comparingLong(PushLogAlarmItem::count).reversed())
                .toList();
    }

    private record PushLogEntry(String channel, String exceptionName, String message, Instant createdDate) {
    }
}
