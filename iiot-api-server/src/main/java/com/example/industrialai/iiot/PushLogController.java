package com.example.industrialai.iiot;

import com.example.industrialai.model.PushLogStatistics;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/iiot/push-logs")
@Validated
public class PushLogController {

    private final PushLogQueryService queryService;

    public PushLogController(PushLogQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/channels")
    public List<String> channels() {
        return queryService.getChannels();
    }

    @GetMapping("/statistics")
    public PushLogStatistics statistics(
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) @Min(1) @Max(365) Integer days) {
        return queryService.getStatistics(channel, from, to, days);
    }
}
