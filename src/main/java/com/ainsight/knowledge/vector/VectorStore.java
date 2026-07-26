package com.ainsight.knowledge.vector;

import java.util.List;

/**
 * 向量存储抽象 —— 本项目"依赖抽象而非实现"的代表作。
 * 当前实现:InMemoryVectorStore(内存暴力余弦,MySQL 持久化,启动加载)。
 * 未来要换 pgvector / Milvus / Elasticsearch,只需新写一个实现类,调用方零改动。
 */
public interface VectorStore {

    /** 批量加入索引 */
    void add(List<StoredChunk> chunks);

    /**
     * 语义检索:与所有向量算余弦相似度,取 topK,且过滤低于 minScore 的噪声。
     */
    List<SearchHit> search(float[] queryVector, int topK, double minScore);

    /** 删除某个文档的全部切片(删文档时同步清理索引) */
    void deleteByDocument(long documentId);

    /** 当前索引中的切片数量 */
    int size();

    /** 索引中的一个条目 */
    record StoredChunk(long chunkId, long documentId, String docName, String content, float[] vector) {
    }
}
