# 工业 AI Agent Demo

这是一个可本地调试的工业告警分析 Demo，调用链为：

```text
REST/SSE -> Spring AI Agent -> Chat Model
                         |-> MCP Client -> MCP Server -> IIoT API -> push_log
                         `-> RAG -> local ONNX Embedding -> pgvector
```

Demo 只提供只读工业数据工具，不执行启停机、复位或参数下发等控制操作。

## 工程结构

```text
industrial-common/       工业领域 DTO
iiot-api-server/         统一 IIoT REST API（含 push_log 统计），端口 8081
iiot-api-mock/            原有设备数据 Mock REST API（兼容保留）
iiot-mcp-server/         Streamable HTTP MCP Server，端口 8082
industrial-agent-app/    Spring AI + Chat Model + MCP Client + RAG，端口 8080
infra/postgresql/        本地 PostgreSQL/pgvector 初始化脚本
```

## 本地环境

- JDK 17+
- Maven 3.6.3+
- Chat Model API Key
- 使用 `pgvector` profile 时：本地 PostgreSQL 及 pgvector 扩展

本项目不需要 Docker。首次构建会下载 Maven 依赖，首次运行 Agent 还会准备本地 ONNX Embedding 模型。

## 1. 构建

在项目根目录运行：

```powershell
mvn clean install
```

## 2. 快速启动

先在 PowerShell 中设置模型 API Key：

```powershell
$env:ZHIPU_API_KEY = "你的模型 API Key"
```

按顺序打开三个终端：

```powershell
mvn -pl iiot-api-server spring-boot:run
```

```powershell
mvn -f iiot-mcp-server/pom.xml spring-boot:run
```

```powershell
mvn -f industrial-agent-app/pom.xml spring-boot:run
```

默认激活 `pgvector` profile；只验证 API/MCP 链路时可设置 `IIOT_PROVIDER_TYPE=in-memory`。

健康检查：

```text
http://localhost:8081/actuator/health
http://localhost:8082/actuator/health
http://localhost:8080/actuator/health
```

## 3. 使用本地 pgvector

先在 PostgreSQL 中创建数据库并执行初始化脚本：

```powershell
createdb -U postgres industrial_ai
psql -U postgres -d industrial_ai -f infra/postgresql/init.sql
```

设置连接参数后，以 `pgvector` profile 启动 Agent：

```powershell
$env:PGVECTOR_URL = "jdbc:postgresql://localhost:5432/industrial_ai"
$env:PGVECTOR_USERNAME = "postgres"
$env:PGVECTOR_PASSWORD = "postgres"
$env:ZHIPU_API_KEY = "你的模型 API Key"
mvn -f industrial-agent-app/pom.xml spring-boot:run "-Dspring-boot.run.profiles=pgvector"
```

Spring AI 会初始化 `vector_store` 表和 HNSW 索引，应用启动时会把示例手册、告警说明和 SOP 写入向量库。

## 4. 调用 Demo

查询指定 channel 最近 3 天的告警类型统计：

```powershell
Invoke-RestMethod "http://localhost:8081/api/iiot/push-logs/statistics?channel=装配一线&days=3"
```

同步问答：

```powershell
$body = @{
  channel = "装配一线"
  days = 3
  message = "统计最近几天各告警类型的次数，并按次数降序展示"
} | ConvertTo-Json

Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/chat `
  -ContentType "application/json" `
  -Body $body
```

流式 SSE：

```bash
curl -N -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"channel":"装配一线","days":3,"message":"统计告警类型"}'
```

可直接检查 IIoT 数据：

```powershell
Invoke-RestMethod http://localhost:8081/api/iiot/push-logs/channels
Invoke-RestMethod "http://localhost:8081/api/iiot/push-logs/statistics?channel=装配一线&days=3"
```

## 配置项

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `ZHIPU_API_KEY` | `not-configured` | 模型 API Key，实际问答必须设置 |
| `ZHIPU_BASE_URL` | `https://open.bigmodel.cn/api/paas/v4` | OpenAI-compatible Base URL |
| `ZHIPU_CHAT_MODEL` | `glm-4-flash` | 对话模型 |
| `IIOT_PROVIDER_TYPE` | `in-memory` | `in-memory` 本地样例，`jdbc` 查询 PostgreSQL `push_log` |
| `IIOT_DB_URL` | - | `push_log` 数据库 JDBC 地址 |
| `IIOT_DB_USERNAME` | - | `push_log` 数据库用户名 |
| `IIOT_DB_PASSWORD` | - | `push_log` 数据库密码 |
| `IIOT_API_BASE_URL` | `http://localhost:8081` | MCP Server 调用的 IIoT API |
| `MCP_SERVER_BASE_URL` | `http://localhost:8082` | Agent 连接的 MCP Server |
| `PGVECTOR_URL` | `jdbc:postgresql://localhost:5432/industrial_ai` | pgvector JDBC URL |
| `PGVECTOR_USERNAME` | `postgres` | 数据库用户名 |
| `PGVECTOR_PASSWORD` | `postgres` | 数据库密码 |
| `RAG_TOP_K` | `4` | 检索片段数量 |

更多拆分任务见 [INDUSTRIAL_AI_AGENT_TASKS.md](INDUSTRIAL_AI_AGENT_TASKS.md)。

## 当前边界

- 本项目是只读告警分析演示，不连接真实 PLC/SCADA，也不提供写控制工具。
- Chat Model 负责推理与工具选择；告警数量必须由 `push_log` 聚合查询返回。
- `iiot-api-server` 的 `jdbc` 模式使用 `creation_date >= from AND creation_date < to`，并按 `channel + exception_name + msg` 聚合。
- `local-memory` profile 重启后会重新构建索引；`pgvector` profile 持久化向量数据。
- 生产化还需要身份认证、设备级授权、审计、限流、可观测性和人工审批闭环。
