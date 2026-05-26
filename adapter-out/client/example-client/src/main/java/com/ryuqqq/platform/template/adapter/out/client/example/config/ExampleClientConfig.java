package com.ryuqqq.platform.template.adapter.out.client.example.config;

import com.ryuqqq.platform.template.adapter.out.client.example.client.ExampleExternalClientAdapter;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/** Example adapter module configuration. */
@Configuration
@ComponentScan(basePackageClasses = ExampleExternalClientAdapter.class)
@EnableConfigurationProperties(ExampleClientProperties.class)
public class ExampleClientConfig {}
