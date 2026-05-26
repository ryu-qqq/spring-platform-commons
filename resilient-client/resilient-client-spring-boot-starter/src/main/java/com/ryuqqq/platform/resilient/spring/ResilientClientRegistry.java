package com.ryuqqq.platform.resilient.spring;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.ryuqqq.platform.resilient.ResilientClient;

/**
 * Declarative YAML로 등록된 {@link ResilientClient} 빈 조회.
 *
 * <p>개별 빈은 {@code {clientKey}ResilientClient} 이름으로도 주입 가능하다.
 */
public class ResilientClientRegistry {

    private final Map<String, ResilientClient> clients;

    public ResilientClientRegistry(Map<String, ResilientClient> clients) {
        this.clients = Collections.unmodifiableMap(new LinkedHashMap<>(clients));
    }

    public ResilientClient get(String clientKey) {
        return clients.get(clientKey);
    }

    public ResilientClient require(String clientKey) {
        ResilientClient client = clients.get(clientKey);
        if (client == null) {
            throw new IllegalArgumentException("Unknown resilient client: " + clientKey);
        }
        return client;
    }

    public Map<String, ResilientClient> getClients() {
        return clients;
    }

    /** {@code setofCommerce} → {@code setofCommerceResilientClient} */
    public static String toBeanName(String clientKey) {
        Objects.requireNonNull(clientKey, "clientKey");
        if (clientKey.isBlank()) {
            throw new IllegalArgumentException("clientKey must not be blank");
        }
        return clientKey + "ResilientClient";
    }
}
