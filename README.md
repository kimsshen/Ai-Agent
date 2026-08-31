# 工业 AI Agent Demo

这是一个可本地调试的工业设备诊断 Demo，调用链为：

```text
REST/SSE -> Spring AI Agent -> DeepSeek
                         |-> MCP Client -> MCP Server -> IIoT Mock API
                         `-> RAG -> local ONNX Embedding -> pgvector
```

Demo 只提供只读工业数据工具，不执行启停机、复位或参数下发等控制操作。

## 工程结构

```text
industrial-common/       工业领域 DTO
iiot-api-mock/           IIoT Mock REST API，端口 8081
iiot-mcp-server/         Streamable HTTP MCP Server，端口 8082
industrial-agent-app/    Spring AI + DeepSeek + MCP Client + RAG，端口 8080
infra/postgresql/        本地 PostgreSQL/pgvector 初始化脚本
```

## 本地环境

- JDK 17+
- Maven 3.6.3+
- DeepSeek API Key
- 使用 `pgvector` profile 时：本地 PostgreSQL 及 pgvector 扩展

本项目不需要 Docker。首次构建会下载 Maven 依赖，首次运行 Agent 还会准备本地 ONNX Embedding 模型。

## 1. 构建

在项目根目录运行：

```powershell
mvn clean install
```

## 2. 快速启动（内存向量库）

先在 PowerShell 中设置 API Key：

```powershell
$env:DEEPSEEK_API_KEY = "你的 DeepSeek API Key"
```

按顺序打开三个终端：

```powershell
mvn -f iiot-api-mock/pom.xml spring-boot:run
```

```powershell
mvn -f iiot-mcp-server/pom.xml spring-boot:run
```

```powershell
mvn -f industrial-agent-app/pom.xml spring-boot:run
```

默认激活 `local-memory` profile，RAG 使用内存向量库，适合先验证完整调用链。

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
$env:DEEPSEEK_API_KEY = "你的 DeepSeek API Key"
mvn -f industrial-agent-app/pom.xml spring-boot:run "-Dspring-boot.run.profiles=pgvector"
```

Spring AI 会初始化 `vector_store` 表和 HNSW 索引，应用启动时会把示例手册、告警说明和 SOP 写入向量库。

## 4. 调用 Demo

同步问答：

```powershell
$body = @{
  deviceId = "DEV-001"
  question = "分析当前温度和振动异常，并给出安全的排查步骤"
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
  -d '{"deviceId":"DEV-001","question":"分析当前异常并给出处理建议"}'
```

可直接检查 IIoT 数据：

```powershell
Invoke-RestMethod http://localhost:8081/api/iiot/devices/DEV-001/snapshot
Invoke-RestMethod "http://localhost:8081/api/iiot/devices/DEV-001/telemetry/history?limit=10"
```

## 配置项

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `DEEPSEEK_API_KEY` | `not-configured` | DeepSeek API Key，实际问答必须设置 |
| `DEEPSEEK_BASE_URL` | `https://api.deepseek.com` | OpenAI-compatible Base URL |
| `DEEPSEEK_MODEL` | `deepseek-v4-flash` | 对话模型 |
| `IIOT_API_BASE_URL` | `http://localhost:8081` | MCP Server 调用的 IIoT API |
| `MCP_SERVER_BASE_URL` | `http://localhost:8082` | Agent 连接的 MCP Server |
| `PGVECTOR_URL` | `jdbc:postgresql://localhost:5432/industrial_ai` | pgvector JDBC URL |
| `PGVECTOR_USERNAME` | `postgres` | 数据库用户名 |
| `PGVECTOR_PASSWORD` | `postgres` | 数据库密码 |
| `RAG_TOP_K` | `4` | 检索片段数量 |

更多拆分任务见 [INDUSTRIAL_AI_AGENT_TASKS.md](INDUSTRIAL_AI_AGENT_TASKS.md)。

## 当前边界

- 本项目是诊断演示，不连接真实 PLC/SCADA，也不提供写控制工具。
- DeepSeek 负责推理与工具选择；Embedding 使用本地模型，避免依赖未提供的 DeepSeek Embedding API。
- `local-memory` profile 重启后会重新构建索引；`pgvector` profile 持久化向量数据。
- 生产化还需要身份认证、设备级授权、审计、限流、可观测性和人工审批闭环。
