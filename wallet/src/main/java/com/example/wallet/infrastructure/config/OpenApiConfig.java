package com.example.wallet.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI customOpenAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Wallet Ledger API")
                .version("1.0.0")
                .description("RESTful API for wallet management and ledger transaction tracking")
                .contact(
                    new Contact()
                        .name("Wallet Service Team")
                        .email("support@wallet.example.com")
                        .url("https://wallet.example.com"))
                .license(
                    new License()
                        .name("Apache 2.0")
                        .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
        .servers(
            List.of(
                new Server().url("http://localhost:8080").description("Development Server"),
                new Server()
                    .url("https://api.wallet.example.com")
                    .description("Production Server")));
  }
}
