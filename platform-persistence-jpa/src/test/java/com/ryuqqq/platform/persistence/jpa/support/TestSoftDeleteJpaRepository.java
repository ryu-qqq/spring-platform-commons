package com.ryuqqq.platform.persistence.jpa.support;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TestSoftDeleteJpaRepository extends JpaRepository<TestSoftDeleteJpaEntity, Long> {}
