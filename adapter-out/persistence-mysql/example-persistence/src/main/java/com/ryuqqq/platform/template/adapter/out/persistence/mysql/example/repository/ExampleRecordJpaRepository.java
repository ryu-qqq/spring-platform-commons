package com.ryuqqq.platform.template.adapter.out.persistence.mysql.example.repository;

import com.ryuqqq.platform.template.adapter.out.persistence.mysql.example.entity.ExampleRecordJpaEntity;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for {@link ExampleRecordJpaEntity} (command-side seed / tests). */
public interface ExampleRecordJpaRepository extends JpaRepository<ExampleRecordJpaEntity, Long> {}
