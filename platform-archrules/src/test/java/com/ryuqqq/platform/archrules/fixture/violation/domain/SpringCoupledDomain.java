package com.ryuqqq.platform.archrules.fixture.violation.domain;

import org.springframework.stereotype.Component;

/** 위반: 도메인이 Spring에 의존 → DOMAIN_FRAMEWORK_FREE가 잡아야 한다. */
@Component
public class SpringCoupledDomain {}
