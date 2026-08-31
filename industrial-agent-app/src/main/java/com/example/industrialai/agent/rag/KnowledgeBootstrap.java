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

@Component
public class KnowledgeBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBootstrap.class);
    private static final String KNOWLEDGE_FILE = "knowledge/industrial-knowledge.md";

    private final VectorStore vectorStore;

    public KnowledgeBootstrap(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String markdown = new ClassPathResource(KNOWLEDGE_FILE)
                .getContentAsString(StandardCharsets.UTF_8);
        List<Document> documents = parseDocuments(markdown);

        vectorStore.delete(documents.stream().map(Document::getId).toList());
        vectorStore.add(documents);
        log.info("Loaded {} industrial knowledge chunks into {}", documents.size(), vectorStore.getClass().getSimpleName());
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
            if (header.length != 4 || lines.length != 2) {
                throw new IllegalStateException("Invalid knowledge section header: " + lines[0]);
            }

            documents.add(Document.builder()
                    .id(header[0].trim())
                    .text(lines[1].trim())
                    .metadata(Map.of(
                            "deviceModel", header[1].trim(),
                            "documentType", header[2].trim(),
                            "source", header[3].trim()))
                    .build());
        }
        return documents;
    }
}

