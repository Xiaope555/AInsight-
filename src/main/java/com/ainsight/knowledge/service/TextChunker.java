package com.ainsight.knowledge.service;

import com.ainsight.config.RagProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 重叠滑窗切分器。
 * 为什么切分:整篇向量化语义被稀释、prompt 塞不下、检索粒度太粗。
 * 为什么重叠:防止一句话恰好被切断,两个相邻切片各留一段"接头"。
 * 小优化:切点尽量落在句子结束符上,而不是硬切在半句话中间。
 */
@Component
@RequiredArgsConstructor
public class TextChunker {

    private static final String SENTENCE_ENDINGS = "。!?!?;;\n";

    private final RagProperties ragProperties;

    public List<String> split(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        String normalized = text.replace("\r\n", "\n").trim();
        int size = ragProperties.getChunkSize();
        int overlap = ragProperties.getChunkOverlap();

        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + size, normalized.length());
            if (end < normalized.length()) {
                // 在 [start+size/2, end) 内从后往前找句子结束符,找到就在那里断开
                int boundary = lastSentenceEnd(normalized, start + size / 2, end);
                if (boundary > 0) {
                    end = boundary + 1;
                }
            }
            String chunk = normalized.substring(start, end).trim();
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }
            if (end >= normalized.length()) {
                break;
            }
            // 下一片从 end-overlap 开始;max 保证指针必定前进,杜绝死循环
            start = Math.max(end - overlap, start + 1);
        }
        return chunks;
    }

    private int lastSentenceEnd(String text, int from, int to) {
        for (int i = to - 1; i >= from; i--) {
            if (SENTENCE_ENDINGS.indexOf(text.charAt(i)) >= 0) {
                return i;
            }
        }
        return -1;
    }
}
