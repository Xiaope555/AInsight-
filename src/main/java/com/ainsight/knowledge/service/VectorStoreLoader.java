package com.ainsight.knowledge.service;

import com.ainsight.knowledge.entity.KbChunk;
import com.ainsight.knowledge.entity.KbDocument;
import com.ainsight.knowledge.mapper.KbChunkMapper;
import com.ainsight.knowledge.mapper.KbDocumentMapper;
import com.ainsight.knowledge.vector.VectorStore;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 启动加载器:应用启动完成后,把 MySQL 里所有 READY 文档的切片向量加载进内存索引。
 * 这就是"MySQL 持久化 + 内存检索"方案的闭环:重启不丢数据,也不用重新向量化(省钱)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorStoreLoader implements ApplicationRunner {

    private final KbDocumentMapper documentMapper;
    private final KbChunkMapper chunkMapper;
    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<KbDocument> docs = documentMapper.selectList(
                new LambdaQueryWrapper<KbDocument>().eq(KbDocument::getStatus, "READY"));
        if (docs.isEmpty()) {
            log.info("vector store: no READY documents to load");
            return;
        }
        Map<Long, String> nameById = docs.stream()
                .collect(Collectors.toMap(KbDocument::getId, KbDocument::getName));
        List<KbChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<KbChunk>()
                .in(KbChunk::getDocumentId, nameById.keySet())
                .isNotNull(KbChunk::getEmbedding));

        List<VectorStore.StoredChunk> stored = new ArrayList<>();
        for (KbChunk chunk : chunks) {
            float[] vector = objectMapper.readValue(chunk.getEmbedding(), float[].class);
            stored.add(new VectorStore.StoredChunk(chunk.getId(), chunk.getDocumentId(),
                    nameById.get(chunk.getDocumentId()), chunk.getContent(), vector));
        }
        vectorStore.add(stored);
        log.info("vector store loaded on startup: {} chunks from {} documents",
                stored.size(), docs.size());
    }
}
