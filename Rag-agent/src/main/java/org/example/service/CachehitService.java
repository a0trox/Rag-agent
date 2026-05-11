package org.example.service;

import jakarta.annotation.Resource;
import org.example.tool.MD5Util;
import org.example.tool.RedisUtil;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


@Service
public class CachehitService {
    @Resource
    RedisTemplate redisTemplate;
    @Resource
    RedisUtil redisUtil;
    @Resource
    VectorEmbeddingService vectorEmbeddingService;
    private static final Logger logger = LoggerFactory.getLogger(CachehitService.class);//報錯Logger配置

    private static final long CACHE_EXPIRE_SECONDS = 3600 * 24; // Redis 过期时间1天

    /**
     *判斷緩存是否命中，如果命中從緩存中讀取
     */
    public String getAnswerFromCache(String userId, List<Float> currentVector) {
        String listKey = "user:list:" + userId;
        String hashKeyPrefix = "user:session:" + userId + ":";

        // 1. 获取该用户的所有问题Key列表
        List<Object> questionKeys = redisTemplate.opsForList().range(listKey, 0, -1);
        if (questionKeys == null || questionKeys.isEmpty()) return null;

        float bestScore = -1.0f; // 初始化相似度
        String bestAnswer = null;
        float threshold = 0.85f; // 命中阈值

        // 2. 遍历比对
        for (Object qKeyObj : questionKeys) {
            String qKey = (String) qKeyObj;
            Map<Object, Object> cacheData = redisTemplate.opsForHash().entries(hashKeyPrefix + qKey);

            // 获取向量 (注意：如果存入时被 Redis 自动序列化了，这里可能需要转型)
            List<Float> cachedVector = (List<Float>) cacheData.get("vector");

            // 使用你之前定义的 calculateCosineSimilarity 方法
            float score = vectorEmbeddingService.calculateCosineSimilarity(currentVector, cachedVector);

            if (score > bestScore) {
                bestScore = score;
                bestAnswer = (String) cacheData.get("answer");
            }
        }
        String answer=(bestScore > threshold) ? bestAnswer : null;

        if(answer==null){
            logger.info("Redis未命中");
        }else{
            logger.info("Redis語義命中");
        }
        // 3. 超过阈值则命中
        return answer;
    }
    /**
     *将问题向量类型存入Redis
     */
    public void saveToSessionCache(String userId, String question, List<Float> vector, String answer) {
        String redisKey = "user:session:" + userId;
        String questionKey = MD5Util.getMD5(question);

        // 1. 使用 Hash 结构存储
        Map<String, Object> cacheData = new HashMap<>();
        cacheData.put("vector", vector); // 向量作为二进制或序列化数组
        cacheData.put("answer", answer);

        redisTemplate.opsForHash().putAll(redisKey + ":" + questionKey, cacheData);

        // 2. 维持长度为 10 的队列
        redisTemplate.opsForList().rightPush("user:list:" + userId, questionKey);
        if (redisTemplate.opsForList().size("user:list:" + userId) > 10) {
            String oldestKey = (String) redisTemplate.opsForList().leftPop("user:list:" + userId);
            redisTemplate.delete(redisKey + ":" + oldestKey);
        }
    }

    /**
     *将问题String类型存入Redis
     */
    public void SetAnswerToRedis(String redisKey,String answer){
        String cleanAnswer=cleanInvalidChars(answer);
        redisUtil.Setanswertoredis(redisKey, cleanAnswer, CACHE_EXPIRE_SECONDS);
    }

    /**
     * 清洗字符串：移除所有不可见的控制字符（解决Redis序列化非法字符问题）
     */
    public static String cleanInvalidChars(String rawStr) {
        if (rawStr == null || rawStr.isEmpty()) {
            return rawStr;
        }
        // 正则：移除 ASCII 0~31 的控制字符（包括报错的 code 0）
        return rawStr.replaceAll("[\\x00-\\x1F]", "");
    }

}
