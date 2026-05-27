package com.ryuqqq.platform.persistence.jpa.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

/**
 * Platform JPA auto-configuration — auditing + {@link JPAQueryFactory} for adapter-out persistence.
 *
 * <p>Consumers still provide {@code DataSource}, entity scan, and repositories. Wiki:
 * persistence-mysql § config / QueryDslRepository.
 */
@AutoConfiguration
@AutoConfigureAfter(HibernateJpaAutoConfiguration.class)
@ConditionalOnClass({EntityManagerFactory.class, JPAQueryFactory.class})
@EnableJpaAuditing
public class PlatformJpaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(EntityManagerFactory.class)
    public JPAQueryFactory jpaQueryFactory(EntityManagerFactory entityManagerFactory) {
        return new JPAQueryFactory(
                SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory));
    }
}
