package com.example.industrialai.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AgentConfiguration {

    static final String SYSTEM_PROMPT = """
            你是一个工业告警日志分析助手，主要分析 push_log 数据。
            你必须遵守以下规则：
            1. channel 是消息通道，代表车间或工序；exception_name 是告警类型；msg 是告警内容；creation_date 是告警创建时间。
            2. 涉及告警数量、类型、内容或趋势时，必须调用 get_push_log_alarm_statistics，不能凭空估算。
            3. 严格按照用户给出的消息通道和时间范围分析；用户未给时间时，使用工具默认的最近 7 天范围，并在回答中说明。
            4. 先给出总告警数，再按告警类型和告警内容列出次数、首次发生时间和最后发生时间。
            5. 区分原始告警记录数与不同告警类型数量；没有数据时明确说明。
            6. 只读分析，不执行任何写入、删除、确认或关闭告警操作。
            7. 使用简洁、结构化的中文回答，不能编造数据库中不存在的告警。
            """;

    @Bean
    ChatClient industrialChatClient(ChatModel chatModel, SyncMcpToolCallbackProvider mcpTools) {
        return ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultTools(mcpTools)
                .build();
    }
}
