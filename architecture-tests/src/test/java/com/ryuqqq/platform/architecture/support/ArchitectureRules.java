package com.ryuqqq.platform.architecture.support;

import java.util.Arrays;

/** Shared package patterns for hexagonal ArchUnit smoke tests (wiki-aligned). */
public final class ArchitectureRules {

    private ArchitectureRules() {}

    @SafeVarargs
    public static String[] allPackages(String[]... groups) {
        return Arrays.stream(groups).flatMap(Arrays::stream).toArray(String[]::new);
    }

    public static final String[] FRAMEWORK_PACKAGES_FORBIDDEN_IN_DOMAIN = {
        "org.springframework..",
        "jakarta.persistence..",
        "javax.persistence..",
        "org.hibernate..",
        "com.fasterxml.jackson..",
        "jakarta.servlet..",
        "org.apache.ibatis..",
    };

    public static final String[] PERSISTENCE_PACKAGES = {
        "jakarta.persistence..",
        "javax.persistence..",
        "org.hibernate..",
        "org.springframework.data.jpa..",
        "com.querydsl..",
    };

    public static final String[] PLATFORM_ADAPTER_IN_PACKAGES = {
        "com.ryuqqq.platform.web..",
    };

    public static final String[] PLATFORM_BOOTSTRAP_PACKAGES = {
        "com.ryuqqq.platform.bootstrap..",
    };

    public static final String[] PLATFORM_ADAPTER_OUT_PACKAGES = {
        "com.ryuqqq.platform.persistence.jpa..",
        "com.ryuqqq.platform.redis..",
    };
}
