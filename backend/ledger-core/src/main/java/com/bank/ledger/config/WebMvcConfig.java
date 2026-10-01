package com.bank.ledger.config;

import com.bank.ledger.security.IdempotencyHandlerInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final IdempotencyHandlerInterceptor idempotencyHandlerInterceptor;

    public WebMvcConfig(IdempotencyHandlerInterceptor idempotencyHandlerInterceptor) {
        this.idempotencyHandlerInterceptor = idempotencyHandlerInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(idempotencyHandlerInterceptor)
                .addPathPatterns("/api/v1/ledger/mutate/**");
    }
}
