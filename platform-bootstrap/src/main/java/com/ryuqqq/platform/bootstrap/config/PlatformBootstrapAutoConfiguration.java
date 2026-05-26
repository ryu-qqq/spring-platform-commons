package com.ryuqqq.platform.bootstrap.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;

/**
 * Platform bootstrap auto-configuration entry point.
 *
 * <p>Registers {@code application-bootstrap.yml} via {@link PlatformBootstrapEnvironmentPostProcessor}.
 * Actuator and graceful-shutdown defaults are applied from that config; no additional beans required.
 */
@AutoConfiguration
public class PlatformBootstrapAutoConfiguration {}
