package com.shopsmart.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /** A single UTC clock injected wherever "now" matters, so time-based logic is testable. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
