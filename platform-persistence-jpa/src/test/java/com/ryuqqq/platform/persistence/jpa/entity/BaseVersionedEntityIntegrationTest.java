package com.ryuqqq.platform.persistence.jpa.entity;

import com.ryuqqq.platform.persistence.jpa.config.PlatformJpaAutoConfiguration;
import com.ryuqqq.platform.persistence.jpa.support.TestSampleJpaEntity;
import com.ryuqqq.platform.persistence.jpa.support.TestSampleJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(PlatformJpaAutoConfiguration.class)
@EntityScan(basePackageClasses = TestSampleJpaEntity.class)
@EnableJpaRepositories(basePackageClasses = TestSampleJpaRepository.class)
class BaseVersionedEntityIntegrationTest {

    @Autowired
    private TestSampleJpaRepository repository;

    @Test
    @DisplayName("저장 시 Auditing이 createdAt/updatedAt을 채운다")
    void auditingPopulatesTimestamps() {
        TestSampleJpaEntity saved = repository.saveAndFlush(TestSampleJpaEntity.create("sample"));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("동시 수정 시 OptimisticLockingFailureException이 발생한다")
    void optimisticLockFailsOnStaleVersion() {
        TestSampleJpaEntity saved = repository.saveAndFlush(TestSampleJpaEntity.create("v0"));
        Long id = saved.getId();

        saved.rename("v1");
        TestSampleJpaEntity updated = repository.saveAndFlush(saved);

        TestSampleJpaEntity stale =
                TestSampleJpaEntity.reconstitute(
                        id,
                        "conflict",
                        updated.getCreatedAt(),
                        updated.getUpdatedAt(),
                        0L);

        assertThatThrownBy(() -> repository.saveAndFlush(stale))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("업데이트마다 @Version이 증가한다")
    void versionIncrementsOnUpdate() {
        TestSampleJpaEntity saved = repository.saveAndFlush(TestSampleJpaEntity.create("v0"));
        assertThat(saved.getVersion()).isZero();

        saved.rename("v1");
        TestSampleJpaEntity updated = repository.saveAndFlush(saved);

        assertThat(updated.getVersion()).isEqualTo(1L);
    }
}
