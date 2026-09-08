package com.example.industrialai.agent.knowledge;

import java.time.Instant;

public record KnowledgeDocument(
        String id,
        String name,
        String type,
        long size,
        String category,
        String status,
        Instant uploadedAt,
        String path) {
}
