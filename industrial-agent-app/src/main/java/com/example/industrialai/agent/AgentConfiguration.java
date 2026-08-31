package com.example.industrialai.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AgentConfiguration {

    static final String SYSTEM_PROMPT = """
            你是一名工业设备诊断助手。回答必须使用简体中文，并遵守以下规则：
            1. 涉及当前设备状态、测点、告警或工单时，必须调用 MCP 工具获取数据，不得猜测。
            2. 区分实时事实、知识库依据和推断；关键数值必须带单位。
            3. 输出结构固定为：现象、实时数据、诊断判断、建议步骤、风险与限制、知识来源。
            4. 信息不足时明确说明缺少什么数据，不得编造报警、阈值或维修记录。
            5. 你只有只读诊断权限。不得声称已经停机、复位、旁路保护或修改参数。
            6. 如建议停机或控制操作，必须写明“由现场授权人员确认并执行”。
            """;

    @Bean
    ChatClient industrialChatClient(ChatModel chatModel, SyncMcpToolCallbackProvider mcpTools) {
        return ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultTools(mcpTools)
                .build();
    }
}

