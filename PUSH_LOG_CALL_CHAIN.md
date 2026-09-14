# push_log 告警统计调用链

本文说明从 Agent 的 `POST /api/chat` 请求，到 PostgreSQL 执行 `push_log` 统计 SQL，再返回最终答案的完整调用过程。

## 1. 完整调用链

```text
客户端
  |
  | POST /api/chat
  | { channel, days/from/to, message }
  v
industrial-agent-app
  |
  | IndustrialChatController.chat()
  v
IndustrialAgentService
  |
  | 1. IndustrialRagService.search()
  |    └─ 从向量库检索辅助知识，生成 sources
  |
  | 2. buildPrompt()
  |    └─ 拼接 channel、时间范围、用户问题和辅助知识
  |
  | 3. ChatClient.prompt().call()
  v
大模型
  |
  | 根据提示词决定调用 MCP 工具
  v
Spring AI MCP Client
  |
  | MCP 协议调用 http://localhost:8081/mcp
  v
iiot-api-server
  |
  | PushLogReadOnlyTools.getPushLogAlarmStatistics()
  v
PushLogQueryService
  |
  | 解析 channel、from、to、days
  | 计算实际的 from/to 时间范围
  v
JdbcPushLogDataProvider
  |
  | 生成参数化 SQL
  | JdbcTemplate.query()
  v
PostgreSQL
  |
  | 查询 push_log
  | 按 channel + exception_name + msg 分组统计
  v
PushLogStatistics
  |
  | MCP -> 大模型
  | 大模型根据真实统计结果组织中文答案
  v
ChatResponse
  |
  | { channel, answer, sources }
  v
客户端
```

## 2. 各层职责

| 层级 | 服务/类 | 主要职责 |
|---|---|---|
| Agent 接入层 | `IndustrialChatController` | 接收 `/api/chat` 请求，返回答案 |
| Agent 编排层 | `IndustrialAgentService` | 执行 RAG、构造提示词、调用大模型 |
| RAG 层 | `IndustrialRagService` | 从向量库检索辅助知识，生成 `sources` |
| MCP 工具层 | `PushLogReadOnlyTools` | 向大模型暴露工具，并直接调用业务层 |
| API 控制层 | `PushLogController` | 接收 push_log 统计请求 |
| 业务层 | `PushLogQueryService` | 标准化 channel 和时间范围 |
| 数据访问层 | `JdbcPushLogDataProvider` | 拼接并执行参数化 SQL |
| 数据库 | PostgreSQL `push_log` | 提供原始告警记录 |

## 3. `/api/chat` 请求

请求地址：

```text
POST http://localhost:8080/api/chat
Content-Type: application/json
```

按最近天数查询：

```json
{
  "channel": "365601",
  "days": 3,
  "message": "统计最近3天各告警类型的次数，并按次数降序展示"
}
```

按精确时间范围查询：

```json
{
  "channel": "365601",
  "from": "2026-09-01T00:00:00Z",
  "to": "2026-09-04T00:00:00Z",
  "message": "统计告警类型和告警次数"
}
```

## 4. Agent 中的两个分支

### 4.1 RAG 分支

`IndustrialAgentService` 首先调用：

```java
List<RagSource> sources = ragService.search(searchQuery(request));
```

RAG 从向量库中检索告警字段说明、统计规则等知识，并将结果放入：

```json
"sources": [
  {
    "id": "...",
    "text": "...",
    "source": "push_log 字段说明",
    "documentType": "data-dictionary",
    "score": 0.71
  }
]
```

RAG 只能解释字段和统计方法，不能作为告警数量来源。

### 4.2 MCP 工具分支

大模型看到提示词中的统计任务后，调用：

```text
get_push_log_alarm_statistics
```

工具参数包括：

```text
channel
from
to
days
```

MCP Server 与 REST API 位于同一个 `iiot-api-server` 进程中。MCP Tool 直接调用
`PushLogQueryService`，REST Controller 也调用同一业务层，两种入口共享完全一致的统计口径。

## 5. API 层时间处理

`PushLogQueryService` 会将请求转换为统一的半开时间范围：

```text
creation_date >= from AND creation_date < to
```

参数规则：

| 请求参数 | 处理方式 |
|---|---|
| `channel` | 去除首尾空格；为空时统计全部 channel |
| `from`、`to` | 使用用户传入的精确时间 |
| `days` | 未提供精确时间时，计算最近 N 天 |
| 都没有提供 | 默认查询最近 7 天 |

## 6. SQL 生成与执行

真正执行 SQL 的位置：

```text
iiot-api-server/src/main/java/com/example/industrialai/iiot/JdbcPushLogDataProvider.java
```

SQL 结构如下：

```sql
SELECT channel,
       COALESCE(exception_name, '') AS exception_name,
       COALESCE(msg, '') AS msg,
       COUNT(*) AS alarm_count,
       MIN(creation_date) AS first_creation_date,
       MAX(creation_date) AS last_creation_date
FROM push_log
WHERE CAST(creation_date AS TIMESTAMP) >= CAST(? AS TIMESTAMP)
  AND CAST(creation_date AS TIMESTAMP) < CAST(? AS TIMESTAMP)
  AND channel = ?
GROUP BY channel, COALESCE(exception_name, ''), COALESCE(msg, '')
ORDER BY channel NULLS LAST,
         alarm_count DESC,
         exception_name,
         msg;
```

SQL 的查询结构由后端代码控制，时间和 channel 使用参数绑定，不由大模型直接生成 SQL。

当没有传入 `channel` 时，SQL 不追加：

```sql
AND channel = ?
```

## 7. 统计结果结构

API 返回 `PushLogStatistics`：

```json
{
  "channel": "365601",
  "from": "2026-09-01T00:00:00Z",
  "to": "2026-09-04T00:00:00Z",
  "totalCount": 64,
  "channels": [
    {
      "channel": "365601",
      "totalCount": 64,
      "alarms": [
        {
          "exceptionName": "压力设备报警",
          "message": "压力异常",
          "count": 10,
          "firstCreatedDate": "2026-09-01T10:00:00Z",
          "lastCreatedDate": "2026-09-03T12:00:00Z"
        }
      ]
    }
  ]
}
```

其中：

- `totalCount`：原始告警记录总数。
- `alarms[].count`：同一个 `exception_name + msg` 组合的记录数。
- `firstCreatedDate`：该组合首次发生时间。
- `lastCreatedDate`：该组合最后发生时间。
- 同一个 `exception_name` 如果对应多个 `msg`，需要将明细分别展示；类型总数可以对这些明细的 `count` 求和。

## 8. 直接调用 API 的简化链路

如果不经过 Agent 和 MCP，直接调用：

```text
GET /api/iiot/push-logs/statistics?channel=365601&days=3
```

调用链为：

```text
PushLogController
  -> PushLogQueryService
  -> JdbcPushLogDataProvider
  -> JdbcTemplate
  -> PostgreSQL push_log
```

## 9. 内存模式与 JDBC 模式

JDBC 模式：

```powershell
$env:IIOT_PROVIDER_TYPE = "jdbc"
mvn -pl iiot-api-server spring-boot:run
```

此模式会访问 PostgreSQL `push_log` 表。

内存模式：

```powershell
$env:IIOT_PROVIDER_TYPE = "in-memory"
mvn -pl iiot-api-server spring-boot:run
```

此模式使用 `InMemoryPushLogDataProvider` 的样例数据，不执行 SQL。

## 10. 相关代码文件

- [IndustrialChatController.java](industrial-agent-app/src/main/java/com/example/industrialai/agent/IndustrialChatController.java)
- [IndustrialRagService.java](industrial-agent-app/src/main/java/com/example/industrialai/agent/rag/IndustrialRagService.java)
- [PushLogReadOnlyTools.java](iiot-api-server/src/main/java/com/example/industrialai/iiot/mcp/PushLogReadOnlyTools.java)
- [PushLogController.java](iiot-api-server/src/main/java/com/example/industrialai/iiot/PushLogController.java)
- [PushLogQueryService.java](iiot-api-server/src/main/java/com/example/industrialai/iiot/PushLogQueryService.java)
- [JdbcPushLogDataProvider.java](iiot-api-server/src/main/java/com/example/industrialai/iiot/JdbcPushLogDataProvider.java)
