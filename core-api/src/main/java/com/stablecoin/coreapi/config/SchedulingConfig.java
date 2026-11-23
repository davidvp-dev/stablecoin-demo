package com.stablecoin.coreapi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on @Scheduled for the background jobs in the jobs package. Spring's
 * default scheduler has one thread, so jobs never overlap with themselves or
 * each other.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
