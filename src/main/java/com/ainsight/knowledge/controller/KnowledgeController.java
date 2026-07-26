package com.ainsight.knowledge.controller;

import com.ainsight.common.result.Result;
import com.ainsight.knowledge.dto.DocumentResponse;
import com.ainsight.knowledge.dto.SearchResultItem;
import com.ainsight.knowledge.service.KnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "04-知识库接口", description = "需要登录")
@RestController
@RequestMapping("/api/kb")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    @Operation(summary = "上传文档(txt/md/pdf)",
            description = "立即返回 PROCESSING;后台异步切分+向量化,用列表接口轮询 status 变 READY")
    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<DocumentResponse> upload(@RequestPart("file") MultipartFile file) {
        return Result.ok(knowledgeService.upload(file));
    }

    @Operation(summary = "文档列表")
    @GetMapping("/documents")
    public Result<List<DocumentResponse>> list() {
        return Result.ok(knowledgeService.list());
    }

    @Operation(summary = "删除文档", description = "管理员或上传者本人;切片与内存索引同步清理")
    @DeleteMapping("/documents/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "语义检索(直测 RAG 链路)",
            description = "把 query 向量化后在内存索引里做余弦 TopK,返回命中切片与相似度")
    @GetMapping("/search")
    public Result<List<SearchResultItem>> search(@RequestParam String query,
                                                 @RequestParam(required = false) Integer topK) {
        return Result.ok(knowledgeService.semanticSearch(query, topK));
    }
}
