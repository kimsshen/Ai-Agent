# IIoT API Server

统一的、只读的 IIoT 业务 API。该模块对 MCP Server 暴露稳定的设备、测点、告警和工单查询契约。

## 启动

```powershell
mvn -pl iiot-api-server spring-boot:run
```

默认监听 `8081`，也可以通过 `IIOT_API_SERVER_PORT` 或 `IIOT_API_PORT` 修改。

## API

```text
GET /api/iiot/devices
GET /api/iiot/devices/{deviceId}
GET /api/iiot/devices/{deviceId}/snapshot
GET /api/iiot/devices/{deviceId}/telemetry/latest
GET /api/iiot/devices/{deviceId}/telemetry/history?limit=12
GET /api/iiot/devices/{deviceId}/alarms?activeOnly=true
GET /api/iiot/devices/{deviceId}/work-orders
GET /api/iiot/push-logs/channels
GET /api/iiot/push-logs/statistics?channel=...&days=3
GET /api/iiot/push-logs/statistics?channel=...&from=...&to=...
GET /actuator/health
```

`channel` 用于指定车间或工序；未传时统计全部 channel。未传 `from/to` 时，
`days` 表示最近天数，默认 7 天，允许 1-365 天。统计按
`channel + exception_name + msg` 聚合，时间范围为左闭右开区间。

## 接入真实数据源

设备接口依赖 `IiotDataProvider`，push_log 接口依赖 `PushLogDataProvider`。
默认的内存 Provider 只用于服务启动和联调；设置 `IIOT_PROVIDER_TYPE=jdbc` 后，
push_log 统计使用 PostgreSQL 的 `push_log` 表。

数据库连接优先读取 `IIOT_DB_URL`、`IIOT_DB_USERNAME`、`IIOT_DB_PASSWORD`，
未设置时兼容读取 `PGVECTOR_URL`、`PGVECTOR_USERNAME`、`PGVECTOR_PASSWORD`。

不要让 MCP Server 或 Agent 直接依赖厂商 API；厂商字段转换、超时和错误映射应放在 Provider/Adapter 层。
