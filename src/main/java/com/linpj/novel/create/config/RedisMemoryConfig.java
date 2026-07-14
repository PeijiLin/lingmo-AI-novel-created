package com.linpj.novel.create.config;

import com.alibaba.cloud.ai.memory.redis.RedisChatMemoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author HL
 */
@Configuration
public class RedisMemoryConfig
{
    @Value("${spring.data.redis.host}")
    private String host;
    @Value("${spring.data.redis.port}")
    private int port;

    @Value("${spring.data.redis.password}")
    private String password;

    @Bean("redisChatMemoryRepository")
    public RedisChatMemoryRepository redisChatMemoryRepository()
    {
        return RedisChatMemoryRepository.builder()
                    .host(host)
                    .port(port)
                    .password(password)
                    .build();
    }
}
