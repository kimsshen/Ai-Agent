# IIoT Business Service

只读的 push-log 告警统计服务，同时提供 REST API 和 MCP Tools。

```text
GET /api/iiot/push-logs/channels
GET /api/iiot/push-logs/statistics?channel=...&days=7
GET /api/iiot/push-logs/statistics?channel=...&from=...&to=...
POST /mcp
```

`IIOT_PROVIDER_TYPE=in-memory` 用于本地联调；设为 `jdbc` 时从 PostgreSQL `push_log` 表只读统计。

MCP Tool 直接调用 `PushLogQueryService`，与 REST Controller 复用同一业务层和数据访问层，不经过内部 HTTP 转发。
