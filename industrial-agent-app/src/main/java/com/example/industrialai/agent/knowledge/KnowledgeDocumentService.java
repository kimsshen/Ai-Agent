package com.example.industrialai.agent.knowledge;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

@Service
public class KnowledgeDocumentService {
    private static final int DEFAULT_CHUNK_SIZE = 1200;
    private static final int DEFAULT_CHUNK_OVERLAP = 200;
    private static final Set<String> OOXML_TYPES = Set.of("DOCX", "PPTX", "XLSX");
    private static final Logger log = LoggerFactory.getLogger(KnowledgeDocumentService.class);

    private final Path root;
    private final VectorStore vectorStore;
    private final int chunkSize;
    private final int chunkOverlap;
    private final ConcurrentHashMap<String, KnowledgeDocument> documents = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<String>> vectorIds = new ConcurrentHashMap<>();

    public KnowledgeDocumentService(
            @Value("${industrial-ai.knowledge.upload-dir:./data/knowledge}") String uploadDir,
            @Value("${industrial-ai.knowledge.chunk-size:" + DEFAULT_CHUNK_SIZE + "}") int chunkSize,
            @Value("${industrial-ai.knowledge.chunk-overlap:" + DEFAULT_CHUNK_OVERLAP + "}") int chunkOverlap,
            VectorStore vectorStore) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.vectorStore = vectorStore;
        this.chunkSize = Math.max(300, chunkSize);
        this.chunkOverlap = Math.max(0, Math.min(chunkOverlap, this.chunkSize / 2));
        try { Files.createDirectories(root); } catch (Exception e) { throw new IllegalStateException("Cannot create upload directory", e); }
    }

    public List<KnowledgeDocument> list(String keyword, String category) {
        String q = keyword == null ? "" : keyword.toLowerCase(Locale.ROOT);
        return documents.values().stream()
                .filter(d -> q.isBlank() || d.name().toLowerCase(Locale.ROOT).contains(q))
                .filter(d -> category == null || category.isBlank() || "全部文档".equals(category) || d.category().equals(category))
                .sorted(Comparator.comparing(KnowledgeDocument::uploadedAt).reversed()).collect(Collectors.toList());
    }

    public Mono<KnowledgeDocument> save(FilePart file, String category) {
        String id = UUID.randomUUID().toString();
        String name = file.filename().replaceAll("[^\\p{L}\\p{N}._()\\- ]", "_");
        Path target = root.resolve(id + "-" + name).normalize();
        if (!target.startsWith(root)) return Mono.error(new IllegalArgumentException("Invalid filename"));
        String normalizedCategory = category == null || category.isBlank() ? "其他" : category;
        return DataBufferUtils.write(file.content(), target, StandardOpenOption.CREATE_NEW)
                .then(Mono.fromCallable(() -> indexDocument(id, name, normalizedCategory, target))
                        .subscribeOn(Schedulers.boundedElastic()))
                .onErrorResume(error -> Mono.fromRunnable(() -> deleteFileQuietly(target))
                        .then(Mono.error(error)));
    }

    public void delete(String id) throws Exception {
        List<String> ids = vectorIds.remove(id);
        if (ids != null && !ids.isEmpty()) {
            vectorStore.delete(ids);
        }
        KnowledgeDocument document = documents.remove(id);
        if (document != null) Files.deleteIfExists(Paths.get(document.path()));
    }

    private KnowledgeDocument indexDocument(String id, String name, String category, Path target) throws Exception {
        String type = extension(name);
        String text = extractText(target, type);
        List<String> chunks = chunkText(text);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("文档没有可用于检索的文本内容");
        }

        List<Document> vectorDocuments = new ArrayList<>(chunks.size());
        List<String> ids = new ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            String vectorId = UUID.nameUUIDFromBytes((id + ":" + index).getBytes(StandardCharsets.UTF_8)).toString();
            ids.add(vectorId);
            vectorDocuments.add(Document.builder()
                    .id(vectorId)
                    .text(chunks.get(index))
                    .metadata(Map.of(
                            "knowledgeId", id,
                            "source", name,
                            "documentType", type,
                            "category", category,
                            "chunkIndex", index,
                            "chunkCount", chunks.size()))
                    .build());
        }

        try {
            vectorStore.add(vectorDocuments);
        } catch (RuntimeException indexingError) {
            try { vectorStore.delete(ids); } catch (RuntimeException cleanupError) { indexingError.addSuppressed(cleanupError); }
            throw indexingError;
        }
        vectorIds.put(id, List.copyOf(ids));
        KnowledgeDocument document = new KnowledgeDocument(id, name, type, Files.size(target), category,
                "已完成", Instant.now(), target.toString());
        documents.put(id, document);
        return document;
    }

    private String extractText(Path target, String type) throws Exception {
        validateOfficeContainer(target, type);
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, target.getFileName().toString());
        try (TikaInputStream input = TikaInputStream.get(target, metadata)) {
            BodyContentHandler handler = new BodyContentHandler(-1);
            new AutoDetectParser().parse(input, handler, metadata, new ParseContext());
            return handler.toString().replace("\u0000", "").trim();
        } catch (TikaException | SAXException | IOException e) {
            log.warn("Unable to parse knowledge document: name={}, type={}, reason={}",
                    target.getFileName(), type, e.getMessage());
            throw new IllegalArgumentException("\u65e0\u6cd5\u89e3\u6790 " + type
                    + " \u6587\u6863\u3002\u8bf7\u786e\u8ba4\u6587\u4ef6\u672a\u52a0\u5bc6\u3001\u672a\u635f\u574f\uff0c\u4e14\u6269\u5c55\u540d\u4e0e\u5b9e\u9645\u683c\u5f0f\u4e00\u81f4\u3002", e);
        }
    }

    private void validateOfficeContainer(Path target, String type) throws IOException {
        if (!OOXML_TYPES.contains(type)) return;
        String requiredEntry = switch (type) {
            case "DOCX" -> "word/document.xml";
            case "PPTX" -> "ppt/presentation.xml";
            case "XLSX" -> "xl/workbook.xml";
            default -> throw new IllegalStateException("Unexpected OOXML type: " + type);
        };
        try (ZipFile zip = new ZipFile(target.toFile())) {
            if (zip.getEntry("[Content_Types].xml") == null || zip.getEntry(requiredEntry) == null) {
                throw invalidOfficeDocument(type, null);
            }
        } catch (ZipException e) {
            throw invalidOfficeDocument(type, e);
        }
    }

    private IllegalArgumentException invalidOfficeDocument(String type, Exception cause) {
        String message = type
                + " \u6587\u4ef6\u4e0d\u662f\u6709\u6548\u7684 Office Open XML \u6587\u6863\u3002\u8bf7\u4f7f\u7528 Word\u3001WPS \u6216 PowerPoint \u53e6\u5b58\u4e3a\u6807\u51c6\u683c\u5f0f\u540e\u91cd\u8bd5\u3002";
        return cause == null ? new IllegalArgumentException(message) : new IllegalArgumentException(message, cause);
    }

    static List<String> chunkText(String text) {
        return chunkText(text, DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP);
    }

    static List<String> chunkText(String text, int chunkSize, int chunkOverlap) {
        String normalized = text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.isEmpty()) return List.of();
        int safeSize = Math.max(1, chunkSize);
        int safeOverlap = Math.max(0, Math.min(chunkOverlap, safeSize - 1));
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(normalized.length(), start + safeSize);
            if (end < normalized.length()) {
                int boundary = normalized.lastIndexOf('\n', end);
                if (boundary > start + safeSize / 2) end = boundary;
            }
            String chunk = normalized.substring(start, end).trim();
            if (!chunk.isEmpty()) chunks.add(chunk);
            if (end >= normalized.length()) break;
            start = Math.max(start + 1, end - safeOverlap);
        }
        return chunks;
    }

    private void deleteFileQuietly(Path target) {
        try { Files.deleteIfExists(target); } catch (Exception ignored) { }
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1).toUpperCase(Locale.ROOT) : "FILE";
    }
}
