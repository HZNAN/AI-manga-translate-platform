package com.hznan.mamgareader.service;

import com.hznan.mamgareader.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;
    private final AppProperties appProperties;

    private static final String LUA_SCRIPT = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local rate = tonumber(ARGV[2])
            local capacity = tonumber(ARGV[3])
            local requested = tonumber(ARGV[4])
            
            local data = redis.call('hmget', key, 'tokens', 'last_time')
            local tokens = tonumber(data[1])
            local last_time = tonumber(data[2])
            
            if tokens == nil then
                tokens = capacity
                last_time = now
            end
            
            local elapsed = math.max(0, now - last_time)
            tokens = math.min(capacity, tokens + elapsed * rate / 1000)
            
            if tokens >= requested then
                tokens = tokens - requested
                redis.call('hset', key, 'tokens', tostring(tokens))
                redis.call('hset', key, 'last_time', tostring(now))
                redis.call('expire', key, math.ceil(capacity / rate) + 10)
                return 1
            else
                redis.call('hset', key, 'tokens', tostring(tokens))
                redis.call('hset', key, 'last_time', tostring(now))
                redis.call('expire', key, math.ceil(capacity / rate) + 10)
                return 0
            end
            """;

    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);

    /**
     * 尝试获取一个令牌。
     *
     * @param userId 用户ID，每个用户独立限流
     * @return true 表示通过限流，false 表示被限流
     */
    public boolean tryAcquire(Long userId) {
        String key = "rate_limit:translate:" + userId;
        long now = System.currentTimeMillis();
        int rate = appProperties.getRateLimit().getTokensPerSecond();
        int capacity = appProperties.getRateLimit().getCapacity();

        Long result = redisTemplate.execute(SCRIPT,
                List.of(key),
                String.valueOf(now),
                String.valueOf(rate),
                String.valueOf(capacity),
                "1");

        boolean allowed = result != null && result == 1L;
        if (!allowed) {
            log.warn("用户 {} 触发翻译接口限流", userId);
        }
        return allowed;
    }
}
