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

    /** 로깅·메트릭 등 관측성 인프라 — 도메인 커널은 의존 금지(ADR-0006 방어선). */
    public static final String[] OBSERVABILITY_PACKAGES_FORBIDDEN_IN_DOMAIN = {
        "org.slf4j..",
        "ch.qos.logback..",
        "io.micrometer..",
    };

    /**
     * platform-common-domain 입주 허용 패키지(ADR-0006 입주 기준). 루트는 package-info용.
     * 새 패키지(예: 횡단 인프라 어휘) 추가는 이 목록을 의도적으로 고쳐야만 통과 — forcing function.
     */
    public static final String[] COMMON_DOMAIN_ALLOWED_PACKAGES = {
        "com.ryuqqq.platform.common",
        "com.ryuqqq.platform.common.vo..",
        "com.ryuqqq.platform.common.exception..",
        "com.ryuqqq.platform.common.domain..",
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
