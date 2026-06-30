package com.eyelanding.fundamentalengine.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    private SecurityScheme createBearerScheme() {
        return new SecurityScheme().type(SecurityScheme.Type.HTTP)
                .bearerFormat("JWT")
                .scheme("bearer");
    }

    private SecurityScheme createAPIKeyScheme() {
        return new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name("X-API-KEY")
                .scheme("api-key");
    }

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().addSecurityItem(new SecurityRequirement().
                        addList("Bearer Authentication"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication", createBearerScheme())
                        .addSecuritySchemes("ApiKey Authentication", createAPIKeyScheme()))
                .info(new Info().title("OpenAPI definition")
                        .description("OpenAPI definition.")
                        .version("3.0").contact(new Contact().name("tung.nt2@mbageas.life")
                                .email("tung.nt2@mbageas.life")
                                .url("https://www.mbageas.life/"))
                        .license(new License().name("License of API")
                                .url("API license URL")));
    }
}

