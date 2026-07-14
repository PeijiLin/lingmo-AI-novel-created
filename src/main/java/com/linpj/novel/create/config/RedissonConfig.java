package com.linpj.novel.create.config;


import lombok.Data;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 配置redisson 客户端
 * @author HL
 */
@Configuration
@Data
@ConfigurationProperties(prefix = "spring.data.redis")
public class RedissonConfig {

    private String port;

    private String host;

    private String password;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        // 创建配置 不使用集群的redis
         /*config.useClusterServers()
                // use "redis://" for Redis connection
                // use "valkey://" for Valkey connection
                // use "valkeys://" for Valkey SSL connection
                // use "rediss://" for Redis SSL connection
                .addNodeAddress("redis://127.0.0.1:6379");*/
        String redisAddress = String.format("redis://%s:%s",host,port);
        config.useSingleServer()
                .setAddress(redisAddress)
                .setPassword(password)
                .setDatabase(0);
        // 创建实例
        return Redisson.create(config);
    }

}
