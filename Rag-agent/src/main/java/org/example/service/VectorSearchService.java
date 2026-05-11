package org.example.service;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.SearchResults;
import io.milvus.param.R;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import org.example.constant.MilvusConstants;
import org.example.dto.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 向量搜索服务
 * 负责从 Milvus 中搜索相似向量
 */
@Service
public class VectorSearchService {

    private static final Logger logger = LoggerFactory.getLogger(VectorSearchService.class);

    @Autowired
    private MilvusServiceClient milvusClient;

    @Autowired
    private VectorEmbeddingService embeddingService;

    /**
     * 搜索相似文档
     * 
     * @param query 查询文本
     * @param topK 返回最相似的K个结果
     * @return 搜索结果列表
     */
//    public List<SearchResult> searchSimilarDocuments(String query, int topK) {
//        try {
//            logger.info("开始搜索相似文档, 查询: {}, topK: {}", query, topK);
//
//            // 1. 将查询文本向量化
//            List<Float> queryVector = embeddingService.generateQueryVector(query);
//            logger.debug("查询向量生成成功, 维度: {}", queryVector.size());
//
//            // 2. 构建搜索参数
//            SearchParam searchParam = SearchParam.newBuilder()
//                    .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
//                    .withVectorFieldName("vector")
//                    .withVectors(Collections.singletonList(queryVector))
//                    .withTopK(topK)
//                    .withMetricType(io.milvus.param.MetricType.L2)
//                    .withOutFields(List.of("id", "content", "metadata"))
//                    .withParams("{\"nprobe\":10}")
//                    .build();
//
//            // 3. 执行搜索
//            R<SearchResults> searchResponse = milvusClient.search(searchParam);
//
//            if (searchResponse.getStatus() != 0) {
//                throw new RuntimeException("向量搜索失败: " + searchResponse.getMessage());
//            }
//
//            // 4. 解析搜索结果
//            SearchResultsWrapper wrapper = new SearchResultsWrapper(searchResponse.getData().getResults());
//            List<SearchResult> results = new ArrayList<>();
//
//            for (int i = 0; i < wrapper.getRowRecords(0).size(); i++) {
//                SearchResult result = new SearchResult();
//                result.setId((String) wrapper.getIDScore(0).get(i).get("id"));
//                result.setContent((String) wrapper.getFieldData("content", 0).get(i));
//                result.setScore(wrapper.getIDScore(0).get(i).getScore());
//
//                // 解析 metadata
//                Object metadataObj = wrapper.getFieldData("metadata", 0).get(i);
//                if (metadataObj != null) {
//                    result.setMetadata(metadataObj.toString());
//                }
//
//                results.add(result);
//            }
//
//            logger.info("搜索完成, 找到 {} 个相似文档", results.size());
//            return results;
//
//        } catch (Exception e) {
//            logger.error("搜索相似文档失败", e);
//            throw new RuntimeException("搜索失败: " + e.getMessage(), e);
//        }
//    }
    public List<SearchResult> searchSimilarDocuments(String query, int topK) {
        try {
            logger.info("开始混合检索, 查询: {}, topK: {}", query, topK);

            // 1. 向量化检索路 (Dense)
            List<SearchResult> denseResults = searchByVector(query, topK);

            // 2. 词法检索路 (BM25/Sparse)
            List<SearchResult> sparseResults = searchByBM25(query, topK);

            // 3. RRF 结果融合
            List<SearchResult> finalResults = rrfFusion(denseResults, sparseResults, topK);

            logger.info("混合搜索完成, 最终结果数: {}", finalResults.size());
            return finalResults;

        } catch (Exception e) {
            logger.error("搜索失败", e);
            throw new RuntimeException("混合搜索失败: " + e.getMessage());
        }
    }

    /**
     * 支路一：向量检索
     */
    private List<SearchResult> searchByVector(String query, int topK) {
        List<Float> queryVector = embeddingService.generateQueryVector(query);
        SearchParam searchParam = SearchParam.newBuilder()
                .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                .withVectorFieldName("vector") // 稠密向量字段
                .withVectors(Collections.singletonList(queryVector))
                .withTopK(topK)
                .withMetricType(io.milvus.param.MetricType.L2)
                .withOutFields(List.of("id", "content", "metadata"))
                .withParams("{\"nprobe\":10}")
                .build();

        R<SearchResults> response = milvusClient.search(searchParam);
        return parseMilvusData(response);
    }

    /**
     * 支路二：BM25 (稀疏向量) 检索
     */
    private List<SearchResult> searchByBM25(String query, int topK) {
        // 这里的 Map 实际对象现在是 TreeMap
        Map<Long, Float> sparseVector = generateSimpleSparseVector(query);

        SearchParam searchParam = SearchParam.newBuilder()
                .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                .withVectorFieldName("sparse_vector")
                // 此时调用就不会报错了，因为 TreeMap 符合 Milvus 的 SortedMap 要求
                .withSparseFloatVectors(Collections.singletonList((SortedMap<Long, Float>) sparseVector))
                .withTopK(topK)
                .withMetricType(io.milvus.param.MetricType.IP)
                .withOutFields(List.of("id", "content", "metadata"))
                .build();

        R<SearchResults> response = milvusClient.search(searchParam);
        return parseMilvusData(response);
    }

    /**
     * 核心：将 Milvus 返回结果解析为你已有的 SearchResult 实体
     */
    private List<SearchResult> parseMilvusData(R<SearchResults> searchResponse) {
        if (searchResponse.getStatus() != 0) {
            return new ArrayList<>();
        }

        SearchResultsWrapper wrapper = new SearchResultsWrapper(searchResponse.getData().getResults());
        List<SearchResult> results = new ArrayList<>();

        // 获取第一个向量对应的结果列表
        List<SearchResultsWrapper.IDScore> idScores = wrapper.getIDScore(0);
        for (int i = 0; i < idScores.size(); i++) {
            SearchResult result = new SearchResult();
            result.setId(idScores.get(i).getStrID()); // 假设你的 ID 是 String
            result.setContent((String) wrapper.getFieldData("content", 0).get(i));
            result.setScore(idScores.get(i).getScore());

            Object metadataObj = wrapper.getFieldData("metadata", 0).get(i);
            if (metadataObj != null) {
                result.setMetadata(metadataObj.toString());
            }
            results.add(result);
        }
        return results;
    }

    /**
     * 补充：简单的稀疏向量生成逻辑 (基于字符哈希的词频)
     */
    private Map<Long, Float> generateSimpleSparseVector(String text) {
        // 关键点：使用 TreeMap 替代 HashMap，因为它实现了 SortedMap 接口
        Map<Long, Float> sparseVector = new TreeMap<>();
        if (text == null || text.isEmpty()) return sparseVector;

        String[] tokens = text.toLowerCase().split("\\s+");
        for (String token : tokens) {
            long hashId = (long) Math.abs(token.hashCode()); // 取绝对值防止 ID 为负数
            sparseVector.put(hashId, sparseVector.getOrDefault(hashId, 0.0f) + 1.0f);
        }
        return sparseVector;
    }

    /**
     * 核心：RRF 融合逻辑
     */
    private List<SearchResult> rrfFusion(List<SearchResult> list1, List<SearchResult> list2, int topK) {
        Map<String, Double> rrfScores = new HashMap<>();
        Map<String, SearchResult> docMap = new HashMap<>();
        int k = 60;

        for (int i = 0; i < list1.size(); i++) {
            String id = list1.get(i).getId();
            rrfScores.put(id, rrfScores.getOrDefault(id, 0.0) + 1.0 / (k + i + 1));
            docMap.put(id, list1.get(i));
        }

        for (int i = 0; i < list2.size(); i++) {
            String id = list2.get(i).getId();
            rrfScores.put(id, rrfScores.getOrDefault(id, 0.0) + 1.0 / (k + i + 1));
            docMap.putIfAbsent(id, list2.get(i));
        }

        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> docMap.get(entry.getKey()))
                .collect(Collectors.toList());
    }
}
