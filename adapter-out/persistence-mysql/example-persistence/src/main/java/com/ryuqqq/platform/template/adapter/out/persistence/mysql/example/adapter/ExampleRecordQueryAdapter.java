package com.ryuqqq.platform.template.adapter.out.persistence.mysql.example.adapter;

import com.ryuqqq.platform.template.adapter.out.persistence.mysql.example.repository.ExampleRecordQueryDslRepository;
import com.ryuqqq.platform.template.port.out.ExampleRecordPort;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Example outbound adapter using platform-persistence-jpa ({@code BaseVersionedSoftDeleteEntity},
 * {@code JPAQueryFactory}).
 *
 * <p>Requires {@code platform.example.persistence.enabled=true}.
 */
@Component
@ConditionalOnProperty(prefix = "platform.example.persistence", name = "enabled", havingValue = "true")
public class ExampleRecordQueryAdapter implements ExampleRecordPort {

    private final ExampleRecordQueryDslRepository queryDslRepository;

    public ExampleRecordQueryAdapter(ExampleRecordQueryDslRepository queryDslRepository) {
        this.queryDslRepository = queryDslRepository;
    }

    @Override
    public long countActiveRecords() {
        return queryDslRepository.countActive();
    }
}
