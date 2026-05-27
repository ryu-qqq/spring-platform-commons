package com.ryuqqq.platform.template.port.out;

/**
 * Placeholder outbound port for JPA persistence integration.
 *
 * <p>Replace with domain-specific ports (e.g. {@code ProductQueryPort}) per service.
 */
public interface ExampleRecordPort {

    /** Count of non-deleted records. */
    long countActiveRecords();
}
