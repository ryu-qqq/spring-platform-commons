package com.ryuqqq.platform.bootstrap.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Imports {@code application-bootstrap.yml} so consumers receive bootstrap defaults with one dependency.
 */
public class PlatformBootstrapEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String CONFIG_IMPORT_PROPERTY = "spring.config.import";
    static final String BOOTSTRAP_CONFIG_IMPORT = "optional:classpath:application-bootstrap.yml";
    static final String PROPERTY_SOURCE_NAME = "platformBootstrapConfigImport";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }

        String existingImport = environment.getProperty(CONFIG_IMPORT_PROPERTY);
        String mergedImport = mergeConfigImport(existingImport);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(CONFIG_IMPORT_PROPERTY, mergedImport);

        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
    }

    static String mergeConfigImport(String existingImport) {
        if (existingImport == null || existingImport.isBlank()) {
            return BOOTSTRAP_CONFIG_IMPORT;
        }
        if (existingImport.contains("application-bootstrap.yml")) {
            return existingImport;
        }
        return existingImport + "," + BOOTSTRAP_CONFIG_IMPORT;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
