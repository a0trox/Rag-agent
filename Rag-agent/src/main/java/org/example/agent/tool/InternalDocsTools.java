package org.example.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.SearchResult;
import org.example.service.VectorSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InternalDocsTools {

    private static final Logger logger = LoggerFactory.getLogger(InternalDocsTools.class);
    public static final String TOOL_QUERY_INTERNAL_DOCS = "queryInternalDocs";

    private final VectorSearchService vectorSearchService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${rag.top-k:3}")
    private int topK;

    @Autowired
    public InternalDocsTools(VectorSearchService vectorSearchService) {
        this.vectorSearchService = vectorSearchService;
    }

    /**
     * 核心修改点：
     * 1. 描述中加入“强制性”引导词，增加中文描述（如果你的用户问的是中文）。
     * 2. 加入更多场景词汇（如：活动安排、XX计划、内部指南）。
     */
    @Tool(description = "当用户询问关于公司内部文档、规章制度、活动安排、技术手册或项目细节（如X-77计划）时，必须使用此工具。 " +
            "This tool performs RAG to retrieve private knowledge from the internal database.")
    public String queryInternalDocs(
            @ToolParam(description = "搜索查询关键词，例如：'活动日程' 或 '报销流程'")
            String query) {

        // 日志埋点：这是判断 Agent 是否真正“动了手”的关键
        logger.info("[Agent触发工具] 正在检索内部知识库，关键词: '{}', 检索深度: {}", query, topK);

        try {
            // 执行检索
            List<SearchResult> searchResults = vectorSearchService.searchSimilarDocuments(query, topK);

            if (searchResults == null || searchResults.isEmpty()) {
                logger.warn("[检索结果] 未找到与 '{}' 相关的任何文档", query);
                return "{\"status\": \"empty\", \"message\": \"知识库中未找到相关内容，请告知用户无法提供该具体细节。\"}";
            }

            logger.info("[检索成功] 找到 {} 条相关片段", searchResults.size());
            return objectMapper.writeValueAsString(searchResults);

        } catch (Exception e) {
            // 重点：如果是之前的 ClassCastException，这里会捕获并打印
            logger.error("[工具执行异常] 检索过程中发生错误，查询词: {}", query, e);
            return String.format("{\"status\": \"error\", \"message\": \"检索系统异常: %s\"}", e.getMessage());
        }
    }
}