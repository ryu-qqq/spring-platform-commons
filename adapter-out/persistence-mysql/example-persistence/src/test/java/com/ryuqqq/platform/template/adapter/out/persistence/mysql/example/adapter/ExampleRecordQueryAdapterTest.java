package com.ryuqqq.platform.template.adapter.out.persistence.mysql.example.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.ryuqqq.platform.persistence.jpa.config.PlatformJpaAutoConfiguration;
import com.ryuqqq.platform.template.adapter.out.persistence.mysql.example.config.ExamplePersistenceConfig;
import com.ryuqqq.platform.template.adapter.out.persistence.mysql.example.entity.ExampleRecordJpaEntity;
import com.ryuqqq.platform.template.adapter.out.persistence.mysql.example.repository.ExampleRecordJpaRepository;
import com.ryuqqq.platform.template.port.out.ExampleRecordPort;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@ImportAutoConfiguration(PlatformJpaAutoConfiguration.class)
@Import({ExamplePersistenceConfig.class, ExampleRecordQueryAdapterTest.QueryDslTestConfig.class})
@TestPropertySource(properties = "platform.example.persistence.enabled=true")
class ExampleRecordQueryAdapterTest {

    static class QueryDslTestConfig {
        @Bean
        JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
            return new JPAQueryFactory(entityManager);
        }
    }

    @Autowired private ExampleRecordPort exampleRecordPort;

    @Autowired private ExampleRecordJpaRepository jpaRepository;

    @Test
    @DisplayName("활성(비삭제) 레코드 수를 QueryDSL로 조회한다")
    void countActiveRecords() {
        jpaRepository.save(new ExampleRecordJpaEntity("active-1"));
        jpaRepository.save(new ExampleRecordJpaEntity("active-2"));

        assertThat(exampleRecordPort.countActiveRecords()).isEqualTo(2L);
    }
}
