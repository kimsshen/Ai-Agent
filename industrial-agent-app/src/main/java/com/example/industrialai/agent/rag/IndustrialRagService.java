package com.example.industrialai.agent.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IndustrialRagService {

    private final VectorStore vectorStore;
    private final int topK;
    private final double similarityThreshold;

    public IndustrialRagService(
            VectorStore vectorStore,
            @Value("${industrial-ai.rag.top-k:4}") int topK,
            @Value("${industrial-ai.rag.similarity-threshold:0.25}") double similarityThreshold) {
        this.vectorStore = vectorStore;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
    }

    public List<RagSource> search(String query) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build();

        List<Document> documents = vectorStore.similaritySearch(request);
        if (documents == null) {
            return List.of();
        }
        return documents.stream().map(this::toSource).toList();
    }

    public String renderContext(List<RagSource> sources) {
        if (sources.isEmpty()) {
            return "未检索到相关知识片段。";
        }
        StringBuilder context = new StringBuilder();
        for (int index = 0; index < sources.size(); index++) {
            RagSource source = sources.get(index);
            context.append("[知识片段 ").append(index + 1).append("]\n")
                    .append("来源：").append(source.source()).append('\n')
                    .append(source.text()).append("\n\n");
        }
        return context.toString();
    }

    private RagSource toSource(Document document) {
        return new RagSource(
                document.getId(),
                document.getText(),
                String.valueOf(document.getMetadata().getOrDefault("source", "unknown")),
                String.valueOf(document.getMetadata().getOrDefault("documentType", "unknown")),
                document.getScore());
    }

    public record RagSource(
            String id,
            String text,
            String source,
            String documentType,
            Double score) {
    }
}
