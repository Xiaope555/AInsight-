package com.ainsight.knowledge.vector;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存向量库:暴力遍历 + 手写余弦相似度。
 *
 * 复杂度 O(n·d):n 个切片 × d 维向量。1 万切片 × 1024 维,现代 CPU 十几毫秒,
 * 对本项目量级绰绰有余;到百万级再换 ANN 索引(HNSW/IVF)的专业实现,
 * 那是"用一点精度换数量级速度"的近似检索 —— 这就是升级路径,面试可讲。
 */
@Slf4j
@Component
public class InMemoryVectorStore implements VectorStore {

    /** chunkId -> 条目。ConcurrentHashMap:文档异步入库线程与查询线程会并发访问 */
    private final Map<Long, StoredChunk> store = new ConcurrentHashMap<>();

    @Override
    public void add(List<StoredChunk> chunks) {
        chunks.forEach(c -> store.put(c.chunkId(), c));
        log.info("vector store: +{} chunks, total {}", chunks.size(), store.size());
    }

    @Override
    public List<SearchHit> search(float[] queryVector, int topK, double minScore) {
        return store.values().stream()
                .map(c -> new SearchHit(c.chunkId(), c.documentId(), c.docName(), c.content(),
                        cosine(queryVector, c.vector())))
                .filter(hit -> hit.score() >= minScore)
                .sorted(Comparator.comparingDouble(SearchHit::score).reversed())
                .limit(topK)
                .toList();
    }

    @Override
    public void deleteByDocument(long documentId) {
        store.values().removeIf(c -> c.documentId() == documentId);
        log.info("vector store: document {} removed, total {}", documentId, store.size());
    }

    @Override
    public int size() {
        return store.size();
    }

    /**
     * 余弦相似度:cos(A,B) = (A·B) / (|A|·|B|),衡量两个向量方向的接近程度。
     * 语义相近的文本,Embedding 向量方向也接近,值越接近 1 越相似。
     * 这十几行就是整个"语义检索"的数学核心 —— 亲手写过,它就不再是黑盒。
     */
    static double cosine(float[] a, float[] b) {
        if (a.length != b.length) {
            return 0;
        }
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
