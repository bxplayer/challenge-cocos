package com.cocos.broker.infrastructure.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI brokerOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("Cocos Broker API")
                .description("API REST de broker/trading: portfolio, búsqueda de instrumentos y envío/cancelación de órdenes.")
                .version("v1"));
    }
}
