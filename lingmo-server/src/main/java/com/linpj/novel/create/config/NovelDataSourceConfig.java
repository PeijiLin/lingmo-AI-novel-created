package com.linpj.novel.create.config;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
@MapperScan(basePackages = "com.linpj.novel.create.mapper", sqlSessionTemplateRef = "novelSqlSessionTemplate")
public class NovelDataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.novel-system")
    public DataSource novelDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    @Primary
    public SqlSessionFactory novelSqlSessionFactory(@Qualifier("novelDataSource") DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean bean = new MybatisSqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        // 可选：设置 MyBatis-Plus 全局配置
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        bean.setConfiguration(configuration);
        // 与 application.yml 的 id-type: auto 对齐：自定义 SqlSessionFactory 不会自动读 yml 全局配置，
        // 否则未显式声明 @TableId(type=AUTO) 的实体走雪花 ID，前端 JS 会丢精度（>2^53）。
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setDbConfig(new GlobalConfig.DbConfig().setIdType(IdType.AUTO));
        bean.setGlobalConfig(globalConfig);
        return bean.getObject();
    }

    @Bean
    @Primary
    public SqlSessionTemplate novelSqlSessionTemplate(@Qualifier("novelSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }
}