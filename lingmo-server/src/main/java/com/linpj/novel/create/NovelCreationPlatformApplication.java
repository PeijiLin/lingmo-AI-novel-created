package com.linpj.novel.create;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * @author HL
 */
@EnableAspectJAutoProxy(proxyTargetClass=true)
@SpringBootApplication
public class NovelCreationPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(NovelCreationPlatformApplication.class, args);
    }

}
