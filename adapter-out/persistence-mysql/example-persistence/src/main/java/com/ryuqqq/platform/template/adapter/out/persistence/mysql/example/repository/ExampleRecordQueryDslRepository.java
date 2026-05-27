package com.ryuqqq.platform.template.adapter.out.persistence.mysql.example.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ryuqqq.platform.template.adapter.out.persistence.mysql.example.entity.QExampleRecordJpaEntity;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/** Query-side repository using {@link JPAQueryFactory} from platform-persistence-jpa. */
@Repository
@ConditionalOnProperty(prefix = "platform.example.persistence", name = "enabled", havingValue = "true")
public class ExampleRecordQueryDslRepository {

    private static final QExampleRecordJpaEntity EXAMPLE_RECORD = QExampleRecordJpaEntity.exampleRecordJpaEntity;

    private final JPAQueryFactory queryFactory;

    public ExampleRecordQueryDslRepository(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    public long countActive() {
        Long count =
                queryFactory
                        .select(EXAMPLE_RECORD.count())
                        .from(EXAMPLE_RECORD)
                        .where(EXAMPLE_RECORD.deleted.isFalse())
                        .fetchOne();
        return count == null ? 0L : count;
    }
}
