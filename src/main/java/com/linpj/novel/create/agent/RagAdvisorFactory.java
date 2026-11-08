package com.linpj.novel.create.agent;

import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.stereotype.Component;

/**
 * 按书籍过滤的 RAG advisor 工厂（复用现有 metadata.bookId 过滤写法，
 * Critic 检索历史章节片段 / Writer 续写参考共用）
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
                .build();
    }
}
