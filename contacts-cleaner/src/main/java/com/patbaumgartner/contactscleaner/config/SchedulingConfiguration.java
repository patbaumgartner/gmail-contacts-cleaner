package com.patbaumgartner.contactscleaner.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's scheduled task execution only when the built-in scheduler is turned
 * on ({@code contacts-cleaner.scheduler.enabled=true}, e.g. via the {@code server}
 * profile). One-shot container runs keep a minimal footprint without a scheduler
 * thread pool.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "contacts-cleaner.scheduler", name = "enabled", havingValue = "true")
class SchedulingConfiguration {

}
