package com.ryuqqq.platform.web.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * {@code com.ryuqqq.platform.web} bean 등록. bootstrap 또는 {@code @Import}로 활성화.
 */
@Configuration
@ComponentScan(basePackages = "com.ryuqqq.platform.web")
public class PlatformWebConfiguration {}
