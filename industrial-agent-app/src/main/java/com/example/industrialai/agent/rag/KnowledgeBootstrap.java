package com.example.industrialai.agent.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class KnowledgeBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBootstrap.class);
    private static final List<String> KNOWLEDGE_FILES = List.of(
            "knowledge/industrial-knowledge.md",
            "knowledge/push-log-analysis.md");

    private final VectorStore vectorStore;

    public KnowledgeBootstrap(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<Document> documents = new ArrayList<>();
        for (String knowledgeFile : KNOWLEDGE_FILES) {
            String markdown = new ClassPathResource(knowledgeFile)
                    .getContentAsString(StandardCharsets.UTF_8);
            documents.addAll(parseDocuments(markdown));
        }

        vectorStore.delete(documents.stream().map(Document::getId).toList());
        vectorStore.add(documents);
        log.info("Loaded {} knowledge chunks into {}", documents.size(), vectorStore.getClass().getSimpleName());
    }

    static List<Document> parseDocuments(String markdown) {
        List<Document> documents = new ArrayList<>();
        for (String rawSection : markdown.split("(?m)^---\\s*$")) {
            String section = rawSection.trim();
            if (section.isEmpty()) {
                continue;
            }

            String[] lines = section.split("\\R", 2);
            String[] header = lines[0].replace("## DOC:", "").trim().split("\\|");
            if ((header.length != 3 && header.length != 4) || lines.length != 2) {
                throw new IllegalStateException("Invalid knowledge section header: " + lines[0]);
            }

            String knowledgeId = header[0].trim();
            int documentTypeIndex = header.length - 2;
            int sourceIndex = header.length - 1;
            String vectorDocumentId = UUID.nameUUIDFromBytes(knowledgeId.getBytes(StandardCharsets.UTF_8)).toString();

            documents.add(Document.builder()
                    .id(vectorDocumentId)
                    .text(lines[1].trim())
                    .metadata(Map.of(
                            "knowledgeId", knowledgeId,
                            "documentType", header[documentTypeIndex].trim(),
                            "source", header[sourceIndex].trim()))
                    .build());
        }
        return documents;
    }
}
