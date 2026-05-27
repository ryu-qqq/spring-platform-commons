package com.ryuqqq.platform.persistence.jpa.entity;

import com.ryuqqq.platform.persistence.jpa.config.PlatformJpaAutoConfiguration;
import com.ryuqqq.platform.persistence.jpa.support.TestSoftDeleteJpaEntity;
import com.ryuqqq.platform.persistence.jpa.support.TestSoftDeleteJpaRepository;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(PlatformJpaAutoConfiguration.class)
@EntityScan(basePackageClasses = TestSoftDeleteJpaEntity.class)
@EnableJpaRepositories(basePackageClasses = TestSoftDeleteJpaRepository.class)
class BaseSoftDeleteEntityIntegrationTest {

    @Autowired
    private TestSoftDeleteJpaRepository repository;

    @Test
    @DisplayName("신규 저장 시 deleted=false, deletedAt=null")
    void activeByDefault() {
        TestSoftDeleteJpaEntity saved = repository.saveAndFlush(TestSoftDeleteJpaEntity.create("active"));

        assertThat(saved.isDeleted()).isFalse();
        assertThat(saved.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("softDelete 시 deleted/deletedAt이 설정된다")
    void markDeletedPersistsTombstone() {
        TestSoftDeleteJpaEntity saved = repository.saveAndFlush(TestSoftDeleteJpaEntity.create("to-delete"));
        Instant deletedAt = Instant.parse("2026-05-27T00:00:00Z");

        saved.softDelete(deletedAt);
        TestSoftDeleteJpaEntity updated = repository.saveAndFlush(saved);

        assertThat(updated.isDeleted()).isTrue();
        assertThat(updated.getDeletedAt()).isEqualTo(deletedAt);
    }

    @Test
    @DisplayName("undelete 시 deleted=false, deletedAt=null로 복원된다")
    void restoreClearsTombstone() {
        TestSoftDeleteJpaEntity saved = repository.saveAndFlush(TestSoftDeleteJpaEntity.create("restore-me"));
        saved.softDelete(Instant.parse("2026-05-27T00:00:00Z"));
        repository.saveAndFlush(saved);

        saved.undelete();
        TestSoftDeleteJpaEntity restored = repository.saveAndFlush(saved);

        assertThat(restored.isDeleted()).isFalse();
        assertThat(restored.getDeletedAt()).isNull();
    }
}
