package com.linpj.novel.create.agent;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * 按书籍过滤的 RAG advisor 工厂（复用现有 metadata.bookId 过滤写法，
 * Critic 检索历史章节片段 / Writer 续写参考共用）
 *
 * 重要：必须自定义 queryAugmenter。Spring AI 默认的 ContextualQueryAugmenter
 * 在检索结果为空时会把查询改写为 "The user query is outside your knowledge base.
 * Politely inform the user that you can't answer it."（明确指示模型拒答）——
 * 空知识库的书籍 Writer 会全量拒答（实测 deepseek-v4.1-flash 完全照做）。
 * 这里空检索时保持原查询不动；有检索结果时注入中文上下文模板。
 *
 * @author HL
 */
@Component
public class RagAdvisorFactory {

    private final PgVectorStore vectorStore;

    public RagAdvisorFactory(PgVectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * 构建按 bookId 过滤的检索增强 advisor
     */
    public RetrievalAugmentationAdvisor forBook(Long bookId) {
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .vectorStore(vectorStore)
                        .filterExpression(() -> new Filter.Expression(
                                Filter.ExpressionType.EQ,
                                new Filter.Key("metadata.bookId"),
                                new Filter.Value(bookId)))
                        .build())
                .queryAugmenter(this::augmentQuery)
                .build();
    }

    /**
     * 检索为空：保持原查询（不注入任何指令）；
     * 检索非空：原查询 + 中文参考上下文模板。
     */
    private Query augmentQuery(Query query, java.util.List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return query;
        }
        String context = documents.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));
        String augmented = query.text() + "\n\n【参考：与本章相关的本书历史片段（写作时自然呼应，不得直接照抄）】\n"
                + context;
        return new Query(augmented, query.history(), query.context());
    }
}
