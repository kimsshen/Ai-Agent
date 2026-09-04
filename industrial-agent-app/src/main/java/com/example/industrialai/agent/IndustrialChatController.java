package com.example.industrialai.agent;

import com.example.industrialai.agent.rag.IndustrialRagService;
import com.example.industrialai.agent.rag.IndustrialRagService.RagSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

import java.time.Instant;
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
            @NotBlank String channel,
            @NotBlank String message,
            Instant from,
            Instant to,
            @Min(1) @Max(365) Integer days) {
    }

    public record ChatResponse(
            String channel,
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
        return new IndustrialChatController.ChatResponse(request.channel(), answer, sources);
    }

    Flux<String> stream(IndustrialChatController.ChatRequest request) {
        List<RagSource> sources = ragService.search(searchQuery(request));
        return chatClient.prompt()
                .user(buildPrompt(request, sources))
                .stream()
                .content();
    }

    private String searchQuery(IndustrialChatController.ChatRequest request) {
        return request.channel() + " push_log 告警类型 exception_name 告警内容 msg 创建时间 creation_date "
                + request.message();
    }

    private String buildPrompt(IndustrialChatController.ChatRequest request, List<RagSource> sources) {
        String from = request.from() == null ? "未指定（默认最近7天）" : request.from().toString();
        String to = request.to() == null ? "未指定（默认当前时间）" : request.to().toString();
        String days = request.days() == null ? "未指定（默认最近7天）" : request.days().toString();
        return """
                消息通道/车间工序：%s
                开始时间：%s
                结束时间：%s
                最近天数：%s（仅在未指定开始时间时生效）
                用户问题：%s

                push_log 字段定义：channel 是消息通道，exception_name 是告警类型，msg 是告警内容，creation_date 是创建时间。
                下面是可选的告警分析知识，只能用于解释字段和分析方法，不能代替数据库统计：
                %s

                请调用 get_push_log_alarm_statistics 获取真实统计结果。
                回答时给出统计时间范围、总告警数，并按 exception_name 和 msg 展示告警次数、首次发生时间、最后发生时间。
                """.formatted(request.channel(), from, to, days, request.message(), ragService.renderContext(sources));
    }
}
