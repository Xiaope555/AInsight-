package com.ainsight.knowledge.vector;

/** 一次向量检索的命中结果 */
public record SearchHit(long chunkId, long documentId, String docName, String content, double score) {
}
