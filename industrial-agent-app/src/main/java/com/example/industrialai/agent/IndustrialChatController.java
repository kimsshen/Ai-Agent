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
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        return Flux.defer(() -> agentService.stream(request))
                .subscribeOn(Schedulers.boundedElastic())
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

    private static final Pattern EXPLICIT_CHANNEL_PATTERN = Pattern.compile(
            "(?:channel|通道)\\s*(?:为|是|=|:|：)\\s*[“\\\"']?(.+?)(?=\\s*(?:最近|近|过去|从|自|\\d+\\s*(?:天|日|周|个月|月)|[，,。；;？?]|$))",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final ChatClient chatClient;
    private final IndustrialRagService ragService;

    IndustrialAgentService(ChatClient industrialChatClient, IndustrialRagService ragService) {
        this.chatClient = industrialChatClient;
        this.ragService = ragService;
    }

    IndustrialChatController.ChatResponse chat(IndustrialChatController.ChatRequest request) {
        List<String> explicitChannels = extractExplicitChannels(request);
        IndustrialChatController.ChatRequest effectiveRequest = withExplicitChannel(request, explicitChannels);
        List<RagSource> sources = ragService.search(searchQuery(effectiveRequest));
        String answer = chatClient.prompt()
                .user(buildPrompt(effectiveRequest, explicitChannels, sources))
                .call()
                .content();
        return new IndustrialChatController.ChatResponse(effectiveRequest.channel(), answer, sources);
    }

    Flux<String> stream(IndustrialChatController.ChatRequest request) {
        // 从用户问题中提取所有明确指定的 channel，并保留原始名称。
        List<String> explicitChannels = extractExplicitChannels(request);
        // 若用户明确指定了 channel，则优先使用它，覆盖页面默认分析范围。
        IndustrialChatController.ChatRequest effectiveRequest = withExplicitChannel(request, explicitChannels);
        // 根据有效 channel 和用户问题检索相关知识库片段。
        List<RagSource> sources = ragService.search(searchQuery(effectiveRequest));
        return chatClient.prompt()
                // 将通道、时间范围、用户指定的多个 channel、问题和 RAG 上下文交给模型。
                .user(buildPrompt(effectiveRequest, explicitChannels, sources))
                // 以流式方式获取模型响应；过程中可能触发 MCP 工具调用。
                .stream()
                // 仅返回生成的文本分片，由 Controller 通过 SSE 持续推送到浏览器。
                .content();
    }

    private List<String> extractExplicitChannels(IndustrialChatController.ChatRequest request) {
        Matcher matcher = EXPLICIT_CHANNEL_PATTERN.matcher(request.message());
        if (!matcher.find()) {
            return List.of();
        }
        return Arrays.stream(matcher.group(1).split("\\s*(?:和|、|以及|及|,|，)\\s*"))
                .map(value -> value.trim().replaceAll("[”\\\"']+$", ""))
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private IndustrialChatController.ChatRequest withExplicitChannel(
            IndustrialChatController.ChatRequest request, List<String> explicitChannels) {
        if (explicitChannels.isEmpty()) {
            return request;
        }
        return new IndustrialChatController.ChatRequest(
                explicitChannels.get(0), request.message(), request.from(), request.to(), request.days());
    }

    private String searchQuery(IndustrialChatController.ChatRequest request) {
        return request.channel() + " push_log 告警类型 exception_name 告警内容 msg 创建时间 creation_date "
                + request.message();
    }

    private String buildPrompt(
            IndustrialChatController.ChatRequest request,
            List<String> explicitChannels,
            List<RagSource> sources) {
        String from = request.from() == null ? "未指定（默认最近7天）" : request.from().toString();
        String to = request.to() == null ? "未指定（默认当前时间）" : request.to().toString();
        String days = request.days() == null ? "未指定（默认最近7天）" : request.days().toString();
        String requiredChannels = explicitChannels.isEmpty()
                ? "未从问题中提取到多个明确通道，使用页面选择的通道。"
                : String.join("、", explicitChannels);
        return """
                消息通道/车间工序：%s
                用户原文指定的统计通道：%s
                开始时间：%s
                结束时间：%s
                最近天数：%s（仅在未指定开始时间时生效）
                用户问题：%s

                push_log 字段定义：channel 是消息通道，exception_name 是告警类型，msg 是告警内容，creation_date 是创建时间。
                下面是可选的告警分析知识，只能用于解释字段和分析方法，不能代替数据库统计：
                %s

                请调用 get_push_log_alarm_statistics 获取真实统计结果。若“用户原文指定的统计通道”包含多个通道，必须逐个调用工具；调用参数必须逐字使用该列表，不得添加、删除或改写任何文字。
                回答时给出统计时间范围、总告警数，并按 exception_name 和 msg 展示告警次数、首次发生时间、最后发生时间。
                """.formatted(request.channel(), requiredChannels, from, to, days, request.message(), ragService.renderContext(sources));
    }
}
