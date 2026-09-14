# push_log 告警分析重构说明

## 数据范围

主业务表为 `push_log`，使用以下字段：

- `channel`：消息通道，业务上代表车间或工序。
- `exception_name`：告警类型或异常名称。
- `msg`：告警内容。
- `creation_date`：告警创建时间；API 兼容数据库中的 `timestamp` 类型和
  `yyyy-MM-dd HH:mm:ss.SSS` 格式字符串。

统计口径为 `creation_date >= from AND creation_date < to`，并按
`channel + exception_name + msg` 聚合。

## REST API

```text
GET /api/iiot/push-logs/channels
GET /api/iiot/push-logs/statistics
    ?channel=装配一线
    &days=3
```

也可以传入精确的半开时间范围：

```text
GET /api/iiot/push-logs/statistics
    ?channel=装配一线
    &from=2026-09-01T00:00:00Z
    &to=2026-09-04T00:00:00Z
```

不传时间时默认统计最近 7 天；也可以通过 `days` 指定最近 1-365 天。不传 `channel` 时统计全部消息通道。

## 数据库模式

设置 `IIOT_PROVIDER_TYPE=jdbc` 后，`iiot-api-server` 使用 PostgreSQL 的
`push_log` 表；连接配置优先使用 `IIOT_DB_URL`、`IIOT_DB_USERNAME` 和
`IIOT_DB_PASSWORD`，未设置时兼容使用 `PGVECTOR_URL`、`PGVECTOR_USERNAME` 和
`PGVECTOR_PASSWORD`。

```powershell
$env:IIOT_PROVIDER_TYPE = "jdbc"
mvn -pl iiot-api-server spring-boot:run
```

## Agent 问答

Agent 的 `/api/chat` 请求改为：

```json
{
  "channel": "装配一线",
  "days": 3,
  "message": "统计告警类型和告警内容，按次数降序展示"
}
```

Agent 必须通过 MCP 工具 `get_push_log_alarm_statistics` 获取真实统计结果，
不能自行估算告警数量。

MCP Tool 和 REST API 由同一个 `iiot-api-server` 提供。Tool 直接复用
`PushLogQueryService`，不再经过独立 MCP 服务和内部 HTTP 转发。
