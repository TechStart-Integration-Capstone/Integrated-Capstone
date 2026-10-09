package com.bank.t24.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI t24OpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PayPink 2.0 — T24 Core Banking Adapter & Simulator")
                        .version("2.0.0")
                        .description("Authoritative core banking System of Record (SoR): live balances, atomic holds (t24.LOCKED_AMOUNT), and double-entry posting journals (t24.POSTING_JOURNAL)."))
                .addSecurityItem(new SecurityRequirement().addList("BearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("BearerAuth", new SecurityScheme()
                                .name("BearerAuth")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
