# push-log API Server

只读的 push-log 告警统计服务。

```text
GET /api/iiot/push-logs/channels
GET /api/iiot/push-logs/statistics?channel=...&days=7
GET /api/iiot/push-logs/statistics?channel=...&from=...&to=...
```

`IIOT_PROVIDER_TYPE=in-memory` 用于本地联调；设为 `jdbc` 时从 PostgreSQL `push_log` 表只读统计。
