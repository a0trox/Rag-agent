package org.example.tool;

import jakarta.annotation.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class RedisUtil {

    // 使用 @Resource 按名称注入，或者明确指定泛型 <String, String>
    @Resource(name = "redisTemplate")
    private RedisTemplate<String, String> redisTemplate;

    public void Setanswertoredis(String key, String value, long seconds) {
        // 确保 value 不为 null
        if (value != null) {
            redisTemplate.opsForValue().set(key, value, seconds, TimeUnit.SECONDS);
        }
    }

    public String getAnswerg(String key) {
        // 因为配置了 StringRedisSerializer，这里直接强转 String 是安全的
        Object value = redisTemplate.opsForValue().get(key);
        return value == null ? null : value.toString();
    }
}
