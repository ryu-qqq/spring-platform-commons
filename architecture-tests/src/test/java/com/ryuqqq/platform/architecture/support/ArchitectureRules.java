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

    public static final String[] DOMAIN_LAYER_PACKAGES = {
        "com.ryuqqq.platform.template..aggregate..",
        "com.ryuqqq.platform.template..vo..",
        "com.ryuqqq.platform.template..id..",
        "com.ryuqqq.platform.template..exception..",
        "com.ryuqqq.platform.template..query..",
        "com.ryuqqq.platform.template.common.vo..",
        "com.ryuqqq.platform.template.common.exception..",
        "com.ryuqqq.platform.common.vo..",
        "com.ryuqqq.platform.common.exception..",
        "com.ryuqqq.platform.common.domain..",
    };

    public static final String[] APPLICATION_LAYER_PACKAGES = {
        "com.ryuqqq.platform.template..port..",
        "com.ryuqqq.platform.template..service..",
        "com.ryuqqq.platform.template..manager..",
        "com.ryuqqq.platform.template..factory..",
        "com.ryuqqq.platform.template..assembler..",
        "com.ryuqqq.platform.template..validator..",
        "com.ryuqqq.platform.template..internal..",
        "com.ryuqqq.platform.template..dto..",
        "com.ryuqqq.platform.template.common.factory..",
        "com.ryuqqq.platform.template.common.component..",
        "com.ryuqqq.platform.template.common.port..",
        "com.ryuqqq.platform.common.factory..",
        "com.ryuqqq.platform.common.component..",
        "com.ryuqqq.platform.common.port..",
    };

    public static final String[] TEMPLATE_ADAPTER_IN_PACKAGES = {
        "com.ryuqqq.platform.template.adapter.in..",
    };

    public static final String[] TEMPLATE_ADAPTER_OUT_PACKAGES = {
        "com.ryuqqq.platform.template.adapter.out..",
    };

    public static final String[] PLATFORM_ADAPTER_IN_PACKAGES = {
        "com.ryuqqq.platform.web..",
    };

    public static final String[] PLATFORM_BOOTSTRAP_PACKAGES = {
        "com.ryuqqq.platform.bootstrap..",
    };

    public static final String[] PLATFORM_ADAPTER_OUT_PACKAGES = {
        "com.ryuqqq.platform.persistence.jpa..",
    };
}
