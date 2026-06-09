package com.ryuqqq.platform.persistence.jpa.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

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

    @Test
    @DisplayName("소비측이 직접 JPAQueryFactory 빈을 정의하면 ConditionalOnMissingBean 으로 양보한다")
    void backsOffWhenUserDefinesOwnJpaQueryFactory() {
        runner.withBean(
                        "customJpaQueryFactory",
                        JPAQueryFactory.class,
                        () -> mock(JPAQueryFactory.class))
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).hasSingleBean(JPAQueryFactory.class);
                            assertThat(context.getBeanNamesForType(JPAQueryFactory.class))
                                    .containsExactly("customJpaQueryFactory");
                        });
    }

    @Test
    @DisplayName("클래스패스에 JPAQueryFactory(QueryDSL) 가 없으면 @ConditionalOnClass 로 자동설정이 비활성화된다")
    void backsOffWhenJpaQueryFactoryNotOnClasspath() {
        runner.withClassLoader(new FilteredClassLoader(JPAQueryFactory.class))
                .run(
                        context -> {
                            assertThat(context)
                                    .doesNotHaveBean(PlatformJpaAutoConfiguration.class);
                            assertThat(context).doesNotHaveBean(JPAQueryFactory.class);
                        });
    }

    @Test
    @DisplayName("클래스패스에 EntityManagerFactory(JPA) 가 없으면 @ConditionalOnClass 로 자동설정이 비활성화된다")
    void backsOffWhenEntityManagerFactoryNotOnClasspath() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PlatformJpaAutoConfiguration.class))
                .withClassLoader(new FilteredClassLoader(EntityManagerFactory.class))
                .run(
                        context -> {
                            assertThat(context)
                                    .doesNotHaveBean(PlatformJpaAutoConfiguration.class);
                            assertThat(context).doesNotHaveBean(JPAQueryFactory.class);
                        });
    }
}
