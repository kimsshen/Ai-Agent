package com.example.industrialai.mcp;

import com.example.industrialai.model.Alarm;
import com.example.industrialai.model.Device;
import com.example.industrialai.model.DeviceSnapshot;
import com.example.industrialai.model.Telemetry;
import com.example.industrialai.model.WorkOrder;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class IiotReadOnlyTools {

    private final RestClient iiotClient;

    public IiotReadOnlyTools(RestClient iiotRestClient) {
        this.iiotClient = iiotRestClient;
    }

    @McpTool(
            name = "list_devices",
            description = "列出当前工厂可查询的全部工业设备。",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public List<Device> listDevices() {
        return iiotClient.get()
                .uri("/api/iiot/devices")
                .retrieve()
                .body(new ParameterizedTypeReference<>() { });
    }

    @McpTool(
            name = "get_device_status",
            description = "查询指定设备的基本信息、运行状态、最新测点和活动告警。诊断设备问题时优先调用。",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public DeviceSnapshot getDeviceStatus(
            @McpToolParam(description = "设备编号，例如 DEV-001") String deviceId) {
        return iiotClient.get()
                .uri("/api/iiot/devices/{deviceId}/snapshot", normalize(deviceId))
                .retrieve()
                .body(DeviceSnapshot.class);
    }

    @McpTool(
            name = "get_latest_telemetry",
            description = "查询指定设备最新的温度、振动、转速和电流测点。",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public Telemetry getLatestTelemetry(
            @McpToolParam(description = "设备编号，例如 DEV-001") String deviceId) {
        return iiotClient.get()
                .uri("/api/iiot/devices/{deviceId}/telemetry/latest", normalize(deviceId))
                .retrieve()
                .body(Telemetry.class);
    }

    @McpTool(
            name = "get_telemetry_history",
            description = "查询指定设备最近的历史测点，采样间隔为 5 分钟，最多返回 60 条。",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public List<Telemetry> getTelemetryHistory(
            @McpToolParam(description = "设备编号，例如 DEV-001") String deviceId,
            @McpToolParam(description = "返回条数，范围 1 到 60") int limit) {
        return iiotClient.get()
                .uri(builder -> builder.path("/api/iiot/devices/{deviceId}/telemetry/history")
                        .queryParam("limit", Math.max(1, Math.min(limit, 60)))
                        .build(normalize(deviceId)))
                .retrieve()
                .body(new ParameterizedTypeReference<>() { });
    }

    @McpTool(
            name = "list_active_alarms",
            description = "查询指定设备当前仍处于活动状态的告警。",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public List<Alarm> listActiveAlarms(
            @McpToolParam(description = "设备编号，例如 DEV-001") String deviceId) {
        return iiotClient.get()
                .uri("/api/iiot/devices/{deviceId}/alarms?activeOnly=true", normalize(deviceId))
                .retrieve()
                .body(new ParameterizedTypeReference<>() { });
    }

    @McpTool(
            name = "query_work_orders",
            description = "查询指定设备的历史维修工单，用于参考相似故障及既往处理措施。",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public List<WorkOrder> queryWorkOrders(
            @McpToolParam(description = "设备编号，例如 DEV-001") String deviceId) {
        return iiotClient.get()
                .uri("/api/iiot/devices/{deviceId}/work-orders", normalize(deviceId))
                .retrieve()
                .body(new ParameterizedTypeReference<>() { });
    }

    private String normalize(String deviceId) {
        if (deviceId == null || !deviceId.matches("DEV-\\d{3}")) {
            throw new IllegalArgumentException("设备编号格式必须为 DEV-加三位数字");
        }
        return deviceId.toUpperCase();
    }
}
