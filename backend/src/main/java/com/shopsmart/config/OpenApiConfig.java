package com.shopsmart.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    OpenAPI shopSmartOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ShopSmart API")
                        .version("v1")
                        .description("""
                                REST API for the ShopSmart e-commerce and order management platform.

                                Authenticate with `POST /api/auth/login`, then send the returned access token as
                                `Authorization: Bearer <token>`. Successful responses use the envelope
                                `{"success": true, "data": ..., "message": ...}`; errors use
                                `{"success": false, "message", "code", "timestamp", "path"}`.
                                """))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
