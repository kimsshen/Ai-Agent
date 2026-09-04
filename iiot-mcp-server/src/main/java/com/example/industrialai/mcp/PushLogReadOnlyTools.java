package com.example.industrialai.mcp;

import com.example.industrialai.model.PushLogStatistics;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PushLogReadOnlyTools {

    private final PushLogApiClient apiClient;

    public PushLogReadOnlyTools(PushLogApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @McpTool(
            name = "list_push_log_channels",
            description = "列出 push_log 中可用的消息通道。消息通道代表车间或工序。",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public List<String> listPushLogChannels() {
        return apiClient.findChannels();
    }

    @McpTool(
            name = "get_push_log_alarm_statistics",
            description = "按消息通道、时间范围统计 push_log 的告警类型 exception_name、告警内容 msg、告警次数以及首次和最后发生时间。",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public PushLogStatistics getPushLogAlarmStatistics(
            @McpToolParam(required = false, description = "消息通道，也就是车间或工序名称；不提供时统计所有通道") String channel,
            @McpToolParam(required = false, description = "开始时间，ISO-8601 格式，例如 2026-09-01T00:00:00Z；可为空") String from,
            @McpToolParam(required = false, description = "结束时间，ISO-8601 格式，例如 2026-09-04T00:00:00Z；可为空") String to,
            @McpToolParam(required = false, description = "最近天数；未提供开始/结束时间时生效，默认 7 天，范围 1-365") Integer days) {
        return apiClient.findStatistics(channel, from, to, days);
    }
}
