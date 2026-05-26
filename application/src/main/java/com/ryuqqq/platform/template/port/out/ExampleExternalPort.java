package com.ryuqqq.platform.template.port.out;

/**
 * Placeholder outbound port for external HTTP integration.
 *
 * <p>Replace with domain-specific ports (e.g. {@code PaymentGatewayPort}) per service.
 */
public interface ExampleExternalPort {

    /** Probe endpoint body when remote responds 2xx. */
    String fetchHealthBody();
}
