package com.example.industrialai.agent;

import com.example.industrialai.agent.rag.IndustrialRagService;
import com.example.industrialai.agent.rag.IndustrialRagService.RagSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.ai.chat.client.ChatClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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
            String channel,
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

    private static final Logger log = LoggerFactory.getLogger(IndustrialAgentService.class);

    private static final Pattern EXPLICIT_CHANNEL_PATTERN = Pattern.compile(
            "(?:channel|通道)\\s*(?:为|是|=|:|：)\\s*[“\\\"']?(.+?)(?=\\s*(?:最近|近|过去|从|自|\\d+\\s*(?:天|日|周|个月|月)|[，,。；;？?]|$))",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern ALARM_DATA_QUERY_PATTERN = Pattern.compile(
            "(?s)(?=.*(?:push[_ -]?log|告警|报警))"
                    + "(?=.*(?:统计|汇总|数量|多少|趋势|柱状图|折线图|图表|分布|占比|排名|合计)).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final ChatClient chatClient;
    private final IndustrialRagService ragService;

    IndustrialAgentService(ChatClient industrialChatClient, IndustrialRagService ragService) {
        this.chatClient = industrialChatClient;
        this.ragService = ragService;
    }

    IndustrialChatController.ChatResponse chat(IndustrialChatController.ChatRequest request) {
        PreparedRequest prepared = prepare(request, "sync");
        long startedAt = System.nanoTime();
        String answer = chatClient.prompt()
                .user(buildPrompt(prepared.request(), prepared.explicitChannels(), prepared.sources()))
                .call()
                .content();
        log.info("LLM_RESPONSE requestId={} mode=sync answerChars={} elapsedMs={}",
                prepared.requestId(), length(answer), elapsedMillis(startedAt));
        return new IndustrialChatController.ChatResponse(
                prepared.request().channel(), answer, prepared.sources());
    }

    Flux<String> stream(IndustrialChatController.ChatRequest request) {
        PreparedRequest prepared = prepare(request, "stream");
        long startedAt = System.nanoTime();
        AtomicInteger answerChars = new AtomicInteger();
        return chatClient.prompt()
                // 将通道、时间范围、用户指定的多个 channel、问题和 RAG 上下文交给模型。
                .user(buildPrompt(prepared.request(), prepared.explicitChannels(), prepared.sources()))
                // 以流式方式获取模型响应；过程中可能触发 MCP 工具调用。
                .stream()
                // 仅返回生成的文本分片，由 Controller 通过 SSE 持续推送到浏览器。
                .content()
                .doOnNext(chunk -> answerChars.addAndGet(length(chunk)))
                .doOnComplete(() -> log.info(
                        "LLM_RESPONSE requestId={} mode=stream answerChars={} elapsedMs={}",
                        prepared.requestId(), answerChars.get(), elapsedMillis(startedAt)))
                .doOnError(error -> log.warn(
                        "LLM_RESPONSE_FAILED requestId={} mode=stream errorType={} message={}",
                        prepared.requestId(), error.getClass().getSimpleName(), safe(error.getMessage(), 300)))
                .onErrorResume(error -> {
                    String fallback = isTimeout(error)
                            ? "模型服务响应超时，请稍后重试。查询尚未完成，不能据此判断没有告警数据。"
                            : "模型服务暂时不可用，请稍后重试。查询尚未完成，不能据此判断没有告警数据。";
                    log.info("LLM_FALLBACK requestId={} reason={} message={}",
                            prepared.requestId(), isTimeout(error) ? "timeout" : "model_error", fallback);
                    return Flux.just(fallback);
                });
    }

    private PreparedRequest prepare(IndustrialChatController.ChatRequest request, String mode) {
        String requestId = UUID.randomUUID().toString();
        List<String> explicitChannels = extractExplicitChannels(request);
        IndustrialChatController.ChatRequest effectiveRequest = withExplicitChannel(request, explicitChannels);
        log.info("CHAT_INPUT requestId={} mode={} message={} requestChannel={} from={} to={} days={}",
                requestId, mode, safe(request.message(), 500), request.channel(),
                request.from(), request.to(), request.days());
        log.info("LLM_CONTEXT requestId={} explicitChannels={} effectiveChannel={} from={} to={} days={}",
                requestId, explicitChannels, effectiveRequest.channel(), effectiveRequest.from(),
                effectiveRequest.to(), effectiveRequest.days());

        boolean skipRag = isAlarmDataQuery(effectiveRequest.message());
        List<RagSource> sources = skipRag
                ? List.of()
                : ragService.search(searchQuery(effectiveRequest));
        if (skipRag) {
            log.info("RAG_RESULT requestId={} skipped=true reason=push_log_data_query hitCount=0", requestId);
        }
        List<String> hitSummary = sources.stream()
                .map(source -> "id=" + source.id()
                        + ",source=" + safe(source.source(), 160)
                        + ",type=" + source.documentType()
                        + ",score=" + source.score())
                .toList();
        if (!skipRag) {
            log.info("RAG_RESULT requestId={} skipped=false hitCount={} hits={}",
                    requestId, sources.size(), hitSummary);
        }
        log.info("LLM_DISPATCH requestId={} ragCandidates={} mcpDecision=pending; "
                        + "actual MCP calls are logged as MCP_TOOL_CALL",
                requestId, sources.size());
        return new PreparedRequest(requestId, effectiveRequest, explicitChannels, sources);
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }

    private boolean isAlarmDataQuery(String message) {
        return message != null && ALARM_DATA_QUERY_PATTERN.matcher(message).matches();
    }

    private boolean isTimeout(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (current instanceof java.io.InterruptedIOException
                    || message != null && message.toLowerCase(java.util.Locale.ROOT).contains("timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private String safe(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace('\r', ' ').replace('\n', ' ');
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength) + "...";
    }

    private record PreparedRequest(
            String requestId,
            IndustrialChatController.ChatRequest request,
            List<String> explicitChannels,
            List<RagSource> sources) {
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
        return request.message();
    }

    private String buildPrompt(
            IndustrialChatController.ChatRequest request,
            List<String> explicitChannels,
            List<RagSource> sources) {
        String from = request.from() == null ? "未指定（默认最近7天）" : request.from().toString();
        String to = request.to() == null ? "未指定（默认当前时间）" : request.to().toString();
        String days = request.days() == null ? "未指定（默认最近7天）" : request.days().toString();
        String selectedChannel = request.channel() == null || request.channel().isBlank()
                ? "未选择"
                : request.channel();
        String requiredChannels = explicitChannels.isEmpty()
                ? "用户未在问题中明确指定通道；如需查询实际数据，可使用页面当前选择的通道。"
                : String.join("、", explicitChannels);
        return """
                用户问题：%s

                页面上下文（仅在用户要求查询实际工业数据时使用，回答通用问题时必须忽略）：
                - 当前消息通道/车间工序：%s
                - 用户原文明确指定的统计通道：%s
                - 开始时间：%s
                - 结束时间：%s
                - 最近天数：%s（仅在未指定开始时间时生效）

                以下是可选知识库片段。仅在与用户问题相关时引用或使用，不相关时忽略：
                %s

                先判断问题类型：通用问题直接回答；只有用户要求真实 push_log 告警数据时才调用相应只读工具。
                查询多个明确通道时逐个调用工具，并原样传递通道名称。
                用户要求图表或可视化时，按用户指定类型或分析目的选择合适的图形，并遵照系统图表协议输出 chart JSON 代码块。实际业务数据必须来自工具；每日趋势用 dailyCounts，分类对比或占比用相应真实统计，不能自行编造数据。
                答案应贴合当前问题自然表达，不套用固定报表格式，也不要在行尾添加反斜杠。
                """.formatted(request.message(), selectedChannel, requiredChannels, from, to, days,
                ragService.renderContext(sources));
    }
}
