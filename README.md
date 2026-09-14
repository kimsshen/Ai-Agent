# 知识库助手

这是一个基于 Spring AI 的知识库问答应用，支持文档 RAG 和只读 push-log 告警统计。设备、测点和工单查询链路已移除。

```text
Client -> industrial-agent-app -> Chat Model
                    |          -> RAG -> Vector Store
                    `-- MCP --> iiot-api-server -> PostgreSQL
                                 `-> REST API
```

运行时只有两个服务：Agent 服务和 IIoT 业务服务。MCP 是 IIoT 业务服务提供的一种接口，不再单独部署中转服务。

## 工程结构

```text
industrial-common/       push-log 统计 DTO
iiot-api-server/         push-log 业务、REST API 与 MCP Server，端口 8081
industrial-agent-app/    知识库、MCP 客户端与问答页面，端口 8080
infra/postgresql/        pgvector 初始化脚本
```

## 环境要求

- JDK 17+
- Maven 3.6.3+
- OpenAI-compatible 聊天和嵌入模型 API
- 使用 `pgvector` profile 时，需要 PostgreSQL 和 pgvector 扩展

## 启动

设置模型和向量库参数：

```powershell
$env:ZHIPU_API_KEY = "你的 API Key"
$env:PGVECTOR_URL = "jdbc:postgresql://localhost:5432/industrial_ai"
$env:PGVECTOR_USERNAME = "postgres"
$env:PGVECTOR_PASSWORD = "postgres"
```

初始化 pgvector 扩展：

```powershell
createdb -U postgres industrial_ai
psql -U postgres -d industrial_ai -f infra/postgresql/init.sql
```

构建后，分别启动 IIoT 业务服务和 Agent：

```powershell
mvn clean verify
mvn -pl iiot-api-server spring-boot:run
mvn -f industrial-agent-app/pom.xml spring-boot:run
```

默认访问 `http://localhost:8080`。本地无 pgvector 时可使用内存向量库：

```powershell
mvn -f industrial-agent-app/pom.xml spring-boot:run "-Dspring-boot.run.profiles=local-memory"
```

## API

```text
POST   /api/chat
POST   /api/chat/stream
GET    /api/documents
POST   /api/documents
DELETE /api/documents/{id}
GET    /api/iiot/push-logs/channels
GET    /api/iiot/push-logs/statistics
POST   /mcp
GET    /actuator/health
```

## 常用配置

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `ZHIPU_API_KEY` | `not-configured` | 模型 API Key |
| `ZHIPU_BASE_URL` | `https://open.bigmodel.cn/api/paas/v4` | OpenAI-compatible Base URL |
| `ZHIPU_CHAT_MODEL` | `glm-4-flash` | 对话模型 |
| `ZHIPU_EMBEDDING_MODEL` | `embedding-3` | 嵌入模型 |
| `IIOT_PROVIDER_TYPE` | `in-memory` | push-log 数据源：`in-memory` 或 `jdbc` |
| `IIOT_MCP_BASE_URL` | `http://localhost:8081` | Agent 访问的 IIoT MCP 地址 |
| `MCP_SERVER_BASE_URL` | — | 旧版兼容变量；建议改用 `IIOT_MCP_BASE_URL` |
| `IIOT_DB_URL` | 使用 `PGVECTOR_URL` | push-log 数据库 JDBC URL |
| `PGVECTOR_URL` | `jdbc:postgresql://localhost:5432/industrial_ai` | pgvector JDBC URL |
| `PGVECTOR_USERNAME` | `postgres` | 数据库用户名 |
| `PGVECTOR_PASSWORD` | `postgres` | 数据库密码 |
| `KNOWLEDGE_UPLOAD_DIR` | `./data/knowledge` | 上传文档目录 |
| `RAG_TOP_K` | `4` | 检索片段数量 |
| `RAG_SIMILARITY_THRESHOLD` | `0.25` | 相似度阈值 |

文档上传支持 PDF、Word、PowerPoint、Excel、TXT 和 Markdown。
