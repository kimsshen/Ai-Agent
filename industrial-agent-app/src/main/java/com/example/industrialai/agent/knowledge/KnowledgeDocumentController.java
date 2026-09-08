package com.example.industrialai.agent.knowledge;

import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class KnowledgeDocumentController {
    private final KnowledgeDocumentService service;
    public KnowledgeDocumentController(KnowledgeDocumentService service) { this.service = service; }

    @GetMapping
    public List<KnowledgeDocument> list(@RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) String category) { return service.list(keyword, category); }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<KnowledgeDocument> upload(@RequestPart("file") FilePart file,
                                          @RequestPart(value = "category", required = false) String category) { return service.save(file, category); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable @NotBlank String id) {
        return Mono.fromRunnable(() -> { try { service.delete(id); } catch (Exception e) { throw new RuntimeException(e); } })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidDocument(IllegalArgumentException error) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_DOCUMENT", error.getMessage()));
    }

    public record ErrorResponse(String code, String message) { }
}
