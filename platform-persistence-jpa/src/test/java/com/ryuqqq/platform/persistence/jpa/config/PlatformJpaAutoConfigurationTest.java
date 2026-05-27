package com.ryuqqq.platform.persistence.jpa.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformJpaAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    DataSourceAutoConfiguration.class,
                                    HibernateJpaAutoConfiguration.class,
                                    PlatformJpaAutoConfiguration.class))
                    .withPropertyValues(
                            "spring.datasource.url=jdbc:h2:mem:platform-jpa-config;DB_CLOSE_DELAY=-1",
                            "spring.jpa.hibernate.ddl-auto=create-drop");

    @Test
    @DisplayName("JPAQueryFactory 빈이 등록된다")
    void registersJpaQueryFactoryBean() {
        runner.run(
                context ->
                        assertThat(context)
                                .hasNotFailed()
                                .hasSingleBean(JPAQueryFactory.class));
    }
}
