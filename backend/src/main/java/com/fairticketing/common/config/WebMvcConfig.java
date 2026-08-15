package com.fairticketing.common.config;

import com.fairticketing.common.ratelimit.HotPathRateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final HotPathRateLimitInterceptor rateLimits;

    public WebMvcConfig(HotPathRateLimitInterceptor rateLimits) {
        this.rateLimits = rateLimits;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimits)
                .addPathPatterns("/api/orders", "/api/waitlist", "/api/waiting-room/*/join");
    }
}
