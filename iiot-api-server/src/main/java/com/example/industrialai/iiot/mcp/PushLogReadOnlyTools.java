package com.example.industrialai.iiot.mcp;

import com.example.industrialai.iiot.PushLogQueryService;
import com.example.industrialai.model.PushLogStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

/** Read-only MCP facade backed directly by the IIoT business service. */
@Component
public class PushLogReadOnlyTools {

    private static final Logger log = LoggerFactory.getLogger(PushLogReadOnlyTools.class);

    private final PushLogQueryService queryService;

    public PushLogReadOnlyTools(PushLogQueryService queryService) {
        this.queryService = queryService;
    }

    @McpTool(
            name = "list_push_log_channels",
            description = "列出 push_log 中可用的消息通道。消息通道代表车间或工序。",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public List<String> listPushLogChannels() {
        String toolCallId = UUID.randomUUID().toString();
        log.info("MCP_TOOL_CALL toolCallId={} tool=list_push_log_channels args={}", toolCallId, "{}");
        List<String> channels = queryService.getChannels();
        log.info("MCP_TOOL_RESULT toolCallId={} tool=list_push_log_channels channelCount={}",
                toolCallId, channels == null ? 0 : channels.size());
        return channels;
    }

    @McpTool(
            name = "get_push_log_alarm_statistics",
            description = "按消息通道、时间范围统计 push_log 告警；返回总数、按日次数 dailyCounts，以及按告警类型和内容聚合的明细。dailyCounts 可直接用于趋势图或柱状图。",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public PushLogStatistics getPushLogAlarmStatistics(
            @McpToolParam(required = false, description = "消息通道，也就是车间或工序名称；不提供时统计所有通道") String channel,
            @McpToolParam(required = false, description = "开始时间，ISO-8601 格式，例如 2026-09-01T00:00:00Z；可为空") String from,
            @McpToolParam(required = false, description = "结束时间，ISO-8601 格式，例如 2026-09-04T00:00:00Z；可为空") String to,
            @McpToolParam(required = false, description = "最近天数；未提供开始/结束时间时生效，默认 7 天，范围 1-365") Integer days) {
        String toolCallId = UUID.randomUUID().toString();
        log.info("MCP_TOOL_CALL toolCallId={} tool=get_push_log_alarm_statistics "
                        + "args[channel={}, from={}, to={}, days={}]",
                toolCallId, channel, from, to, days);
        PushLogStatistics statistics = queryService.getStatistics(
                channel, parseInstant(from, "from"), parseInstant(to, "to"), days);
        log.info("MCP_TOOL_RESULT toolCallId={} tool=get_push_log_alarm_statistics "
                        + "resolvedChannel={} from={} to={} totalCount={} dailyBucketCount={} channelCount={}",
                toolCallId,
                statistics == null ? null : statistics.channel(),
                statistics == null ? null : statistics.from(),
                statistics == null ? null : statistics.to(),
                statistics == null ? null : statistics.totalCount(),
                statistics == null || statistics.dailyCounts() == null ? 0 : statistics.dailyCounts().size(),
                statistics == null || statistics.channels() == null ? 0 : statistics.channels().size());
        return statistics;
    }

    private Instant parseInstant(String value, String parameter) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return Instant.parse(trimmed);
        }
        catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(trimmed).toInstant();
            }
            catch (DateTimeParseException exception) {
                throw new IllegalArgumentException(
                        parameter + " must be a valid ISO-8601 timestamp", exception);
            }
        }
    }
}
