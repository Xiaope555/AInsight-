package com.ainsight.knowledge.service;

import com.ainsight.agent.llm.LlmClient;
import com.ainsight.config.RagProperties;
import com.ainsight.knowledge.entity.KbChunk;
import com.ainsight.knowledge.entity.KbDocument;
import com.ainsight.knowledge.mapper.KbChunkMapper;
import com.ainsight.knowledge.mapper.KbDocumentMapper;
import com.ainsight.knowledge.vector.VectorStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档异步处理流水线:切分 -> 分批向量化 -> 落库 -> 加载进内存索引 -> 更新状态。
 *
 * 为什么独立成类:@Async 依赖 Spring 代理,"同类内部调用异步方法"不会生效
 * (this.process() 绕过了代理)—— 把异步方法放在另一个 Bean 里是标准解法。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentProcessor {

    private final TextChunker textChunker;
    private final LlmClient llmClient;
    private final RagProperties ragProperties;
    private final KbDocumentMapper documentMapper;
    private final KbChunkMapper chunkMapper;
    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper;

    @Async
    public void process(Long documentId, String docName, String text) {
        long start = System.currentTimeMillis();
        try {
            List<String> pieces = textChunker.split(text);
            if (pieces.isEmpty()) {
                markFailed(documentId);
                log.warn("document {} has no content after parsing", documentId);
                return;
            }
            List<VectorStore.StoredChunk> stored = new ArrayList<>();
            int index = 0;
            int batchSize = ragProperties.getEmbeddingBatchSize();
            for (int from = 0; from < pieces.size(); from += batchSize) {
                List<String> batch = pieces.subList(from, Math.min(from + batchSize, pieces.size()));
                List<float[]> vectors = llmClient.embed(batch);
                for (int i = 0; i < batch.size(); i++) {
                    KbChunk chunk = new KbChunk();
                    chunk.setDocumentId(documentId);
                    chunk.setChunkIndex(index++);
                    chunk.setContent(batch.get(i));
                    chunk.setEmbedding(objectMapper.writeValueAsString(vectors.get(i)));
                    chunkMapper.insert(chunk); // insert 后 MP 回填自增 id
                    stored.add(new VectorStore.StoredChunk(
                            chunk.getId(), documentId, docName, batch.get(i), vectors.get(i)));
                }
            }
            vectorStore.add(stored);

            KbDocument update = new KbDocument();
            update.setId(documentId);
            update.setStatus("READY");
            update.setChunkCount(stored.size());
            documentMapper.updateById(update);
            log.info("document {} READY: {} chunks, cost={}ms",
                    documentId, stored.size(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            // 异步方法里的异常没人接,必须自己兜住并把状态置为 FAILED
            log.error("document {} process FAILED", documentId, e);
            markFailed(documentId);
        }
    }

    private void markFailed(Long documentId) {
        KbDocument update = new KbDocument();
        update.setId(documentId);
        update.setStatus("FAILED");
        documentMapper.updateById(update);
    }
}
