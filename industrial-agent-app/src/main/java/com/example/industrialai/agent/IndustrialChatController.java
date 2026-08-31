package com.example.industrialai.agent;

import com.example.industrialai.agent.rag.IndustrialRagService;
import com.example.industrialai.agent.rag.IndustrialRagService.RagSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@Validated
public class IndustrialChatController {

    private final IndustrialAgentService agentService;

    public IndustrialChatController(IndustrialAgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        return Mono.fromCallable(() -> agentService.chat(request))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@Valid @RequestBody ChatRequest request) {
        return agentService.stream(request)
                .map(chunk -> ServerSentEvent.builder(chunk).event("message").build())
                .concatWithValues(ServerSentEvent.builder("[DONE]").event("done").build());
    }

    public record ChatRequest(
            @NotBlank @Pattern(regexp = "DEV-\\d{3}") String deviceId,
            @NotBlank String message) {
    }

    public record ChatResponse(
            String deviceId,
            String answer,
            List<RagSource> sources) {
    }
}

@Service
class IndustrialAgentService {

    private final ChatClient chatClient;
    private final IndustrialRagService ragService;

    IndustrialAgentService(ChatClient industrialChatClient, IndustrialRagService ragService) {
        this.chatClient = industrialChatClient;
        this.ragService = ragService;
    }

    IndustrialChatController.ChatResponse chat(IndustrialChatController.ChatRequest request) {
        List<RagSource> sources = ragService.search(searchQuery(request));
        String answer = chatClient.prompt()
                .user(buildPrompt(request, sources))
                .call()
                .content();
        return new IndustrialChatController.ChatResponse(request.deviceId(), answer, sources);
    }

    Flux<String> stream(IndustrialChatController.ChatRequest request) {
        List<RagSource> sources = ragService.search(searchQuery(request));
        return chatClient.prompt()
                .user(buildPrompt(request, sources))
                .stream()
                .content();
    }

    private String searchQuery(IndustrialChatController.ChatRequest request) {
        return request.deviceId() + " 工业设备异常诊断 " + request.message();
    }

    private String buildPrompt(IndustrialChatController.ChatRequest request, List<RagSource> sources) {
        return """
                设备编号：%s
                用户问题：%s

                以下是 RAG 检索到的内部知识。它只能作为诊断依据，设备当前状态必须调用 MCP 工具查询：
                %s

                请先调用 get_device_status；需要趋势或历史经验时，再调用 get_telemetry_history 和 query_work_orders。
                最终答案必须引用上面的知识来源名称。
                """.formatted(request.deviceId(), request.message(), ragService.renderContext(sources));
    }
}
