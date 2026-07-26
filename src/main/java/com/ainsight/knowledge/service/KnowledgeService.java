package com.ainsight.knowledge.service;

import com.ainsight.agent.llm.LlmClient;
import com.ainsight.common.exception.BizException;
import com.ainsight.common.result.ResultCode;
import com.ainsight.config.RagProperties;
import com.ainsight.knowledge.dto.DocumentResponse;
import com.ainsight.knowledge.dto.SearchResultItem;
import com.ainsight.knowledge.entity.KbChunk;
import com.ainsight.knowledge.entity.KbDocument;
import com.ainsight.knowledge.mapper.KbChunkMapper;
import com.ainsight.knowledge.mapper.KbDocumentMapper;
import com.ainsight.knowledge.vector.VectorStore;
import com.ainsight.security.LoginUser;
import com.ainsight.security.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private static final Set<String> SUPPORTED_TYPES = Set.of("txt", "md", "pdf");

    private final KbDocumentMapper documentMapper;
    private final KbChunkMapper chunkMapper;
    private final DocumentProcessor documentProcessor;
    private final VectorStore vectorStore;
    private final LlmClient llmClient;
    private final RagProperties ragProperties;

    /** 上传:同步解析文本 + 建档,切分/向量化交给异步流水线,接口立即返回 */
    public DocumentResponse upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "文件不能为空");
        }
        String originalName = file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename();
        String ext = extension(originalName);
        if (!SUPPORTED_TYPES.contains(ext)) {
            throw new BizException(ResultCode.UNSUPPORTED_FILE_TYPE);
        }
        String text = extractText(file, ext);

        KbDocument doc = new KbDocument();
        doc.setUserId(SecurityUtils.getUserId());
        doc.setName(originalName);
        doc.setFileType(ext);
        doc.setFileSize(file.getSize());
        doc.setStatus("PROCESSING");
        doc.setChunkCount(0);
        documentMapper.insert(doc);

        documentProcessor.process(doc.getId(), originalName, text); // @Async,立即返回
        log.info("document {} uploaded by user {}, async processing started", doc.getId(), doc.getUserId());
        return toResponse(doc);
    }

    public List<DocumentResponse> list() {
        return documentMapper.selectList(
                        new LambdaQueryWrapper<KbDocument>().orderByDesc(KbDocument::getId))
                .stream().map(this::toResponse).toList();
    }

    /** 删除:管理员或上传者本人;文档逻辑删,切片物理删,内存索引同步清 */
    public void delete(Long id) {
        KbDocument doc = documentMapper.selectById(id); // @TableLogic 自动过滤已删
        if (doc == null) {
            throw new BizException(ResultCode.DOCUMENT_NOT_FOUND);
        }
        LoginUser user = SecurityUtils.getLoginUser();
        if (!"ADMIN".equals(user.role()) && !doc.getUserId().equals(user.id())) {
            throw new BizException(ResultCode.FORBIDDEN, "只能删除自己上传的文档");
        }
        documentMapper.deleteById(id); // 实际执行 UPDATE deleted=1
        chunkMapper.delete(new LambdaQueryWrapper<KbChunk>().eq(KbChunk::getDocumentId, id));
        vectorStore.deleteByDocument(id);
        log.info("document {} deleted by user {}", id, user.id());
    }

    /** 语义检索:查询向量化 -> 内存余弦 TopK(供接口直测,也供 KbSearchTool 复用) */
    public List<SearchResultItem> semanticSearch(String query, Integer topK) {
        int k = (topK != null && topK > 0) ? Math.min(topK, 10) : ragProperties.getTopK();
        float[] queryVector = llmClient.embed(List.of(query)).get(0);
        return vectorStore.search(queryVector, k, ragProperties.getMinScore()).stream()
                .map(hit -> new SearchResultItem(hit.documentId(), hit.docName(), hit.content(),
                        Math.round(hit.score() * 1000.0) / 1000.0))
                .toList();
    }

    private String extractText(MultipartFile file, String ext) {
        try {
            if ("pdf".equals(ext)) {
                try (PDDocument pdf = Loader.loadPDF(file.getBytes())) {
                    return new PDFTextStripper().getText(pdf);
                }
            }
            // txt / md:按 UTF-8 读文本
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("extract text failed: {}", e.getMessage());
            throw new BizException(ResultCode.DOCUMENT_PARSE_FAILED);
        }
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase();
    }

    private DocumentResponse toResponse(KbDocument doc) {
        return new DocumentResponse(doc.getId(), doc.getName(), doc.getFileType(),
                doc.getFileSize(), doc.getStatus(), doc.getChunkCount(), doc.getCreatedAt());
    }
}
