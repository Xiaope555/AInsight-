package com.ainsight.agent.tools;

import com.ainsight.agent.core.AgentTool;
import com.ainsight.agent.core.ToolParam;
import com.ainsight.config.RagProperties;
import com.ainsight.knowledge.dto.SearchResultItem;
import com.ainsight.knowledge.service.KnowledgeService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 工具三:知识库语义检索 —— RAG 与 Agent 在这里合体。
 * 加上 @Component 即自动注册进 ToolRegistry(阶段4架构的红利:新工具=一个类)。
 * 从此模型可以自主决定"这个问题我需要先查内部资料"。
 */
@Component
@RequiredArgsConstructor
public class KbSearchTool implements AgentTool {

    private final KnowledgeService knowledgeService;
    private final RagProperties ragProperties;

    @Data
    public static class Params {
        @ToolParam(description = "要在知识库中检索的问题或关键词,例如:退货政策、保修期限")
        private String query;
    }

    @Override
    public String name() {
        return "search_knowledge";
    }

    @Override
    public String description() {
        return "在企业内部知识库中检索与问题最相关的文档内容(如售后政策、退货规则、保修条款、"
                + "公司制度、产品说明)。当用户的问题涉及公司内部规定或文档知识时使用。";
    }

    @Override
    public Class<?> parameterType() {
        return Params.class;
    }

    @Override
    public String execute(Object args) {
        Params params = (Params) args;
        List<SearchResultItem> hits = knowledgeService.semanticSearch(
                params.getQuery(), ragProperties.getTopK());
        if (hits.isEmpty()) {
            return "知识库中没有检索到与「" + params.getQuery() + "」相关的内容。";
        }
        StringBuilder sb = new StringBuilder("知识库检索结果(请基于以下内容回答,并注明来源文档):\n");
        for (int i = 0; i < hits.size(); i++) {
            SearchResultItem hit = hits.get(i);
            sb.append(String.format("[%d] 来源《%s》(相关度 %.2f):%n%s%n%n",
                    i + 1, hit.docName(), hit.score(), hit.content()));
        }
        return sb.toString();
    }
}
