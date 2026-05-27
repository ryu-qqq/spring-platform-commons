package com.ryuqqq.platform.template.adapter.out.persistence.mysql.example.config;

import com.ryuqqq.platform.template.adapter.out.persistence.mysql.example.adapter.ExampleRecordQueryAdapter;
import com.ryuqqq.platform.template.adapter.out.persistence.mysql.example.entity.ExampleRecordJpaEntity;
import com.ryuqqq.platform.template.adapter.out.persistence.mysql.example.repository.ExampleRecordJpaRepository;
import com.ryuqqq.platform.template.adapter.out.persistence.mysql.example.repository.ExampleRecordQueryDslRepository;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Example adapter-out persistence module configuration. */
@Configuration
@EntityScan(basePackageClasses = ExampleRecordJpaEntity.class)
@EnableJpaRepositories(basePackageClasses = ExampleRecordJpaRepository.class)
@ComponentScan(basePackageClasses = {ExampleRecordQueryAdapter.class, ExampleRecordQueryDslRepository.class})
@EnableConfigurationProperties(ExamplePersistenceProperties.class)
public class ExamplePersistenceConfig {}
