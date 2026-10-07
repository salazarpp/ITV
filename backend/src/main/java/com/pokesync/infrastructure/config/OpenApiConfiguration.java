package com.pokesync.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.headers.Header;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI pokeSyncOpenApi() {
        return new OpenAPI().info(new Info().title("PokeSync API").version("v1")
                .description("Public Pokemon browsing and authenticated local synchronization. "
                        + "Errors contain status, code, message, timestamp and requestId. "
                        + "Every HTTP response includes X-Request-ID."))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"))
                        .addSchemas("ApiError", new ObjectSchema()
                                .addProperty("status", new IntegerSchema())
                                .addProperty("code", new StringSchema())
                                .addProperty("message", new StringSchema())
                                .addProperty("timestamp", new StringSchema().format("date-time"))
                                .addProperty("requestId", new StringSchema())));
    }

    @Bean
    OpenApiCustomizer routeSecurityDocumentation() {
        return api -> {
            if (api.getPaths() == null) return;
            api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
                boolean publicRoute = path.startsWith("/auth/")
                        || (method == io.swagger.v3.oas.models.PathItem.HttpMethod.GET
                        && (path.equals("/api/v1/pokemon") || path.startsWith("/api/v1/pokemon/")));
                operation.setSecurity(publicRoute ? List.of() : List.of(new SecurityRequirement().addList("bearerAuth")));
                operation.addParametersItem(new Parameter().name("X-Request-ID").in("header").required(false)
                        .description("Optional correlation ID; unsafe values are replaced. Returned in every response.")
                        .schema(new StringSchema().pattern("[A-Za-z0-9._-]{1,64}")));
                var responses = operation.getResponses();
                boolean createsRecord = method == io.swagger.v3.oas.models.PathItem.HttpMethod.POST
                        && (path.equals("/auth/register") || path.equals("/api/v1/local-pokemon"));
                if (createsRecord) {
                    ApiResponse created = responses.remove("200");
                    if (created == null) created = new ApiResponse();
                    created.setDescription("Record created");
                    if (path.equals("/api/v1/local-pokemon")) {
                        created.addHeaderObject("Location", new Header().description("Created local record URI")
                                .schema(new StringSchema()));
                    }
                    responses.addApiResponse("201", created);
                }
                if (method == io.swagger.v3.oas.models.PathItem.HttpMethod.DELETE) {
                    responses.remove("200");
                    responses.addApiResponse("204", new ApiResponse().description("Record deleted"));
                }
                responses.addApiResponse("400", failure("Invalid request"));
                responses.addApiResponse("500", failure("Unexpected server failure"));
                if (!publicRoute) {
                    responses.addApiResponse("401", failure("Missing or invalid bearer token"));
                    responses.addApiResponse("403", failure("Access denied"));
                }
                if (path.contains("{id}")) responses.addApiResponse("404", failure("Pokemon was not found"));
                if (path.equals("/auth/register") || path.equals("/api/v1/local-pokemon")
                        && method == io.swagger.v3.oas.models.PathItem.HttpMethod.POST) {
                    responses.addApiResponse("409", failure("Record already exists"));
                }
                if (path.equals("/auth/login")) responses.addApiResponse("401", failure("Invalid credentials"));
                if (path.startsWith("/api/v1/pokemon") || path.equals("/api/v1/local-pokemon")
                        && method == io.swagger.v3.oas.models.PathItem.HttpMethod.POST) {
                    responses.addApiResponse("502", failure("Pokemon provider unavailable"));
                    responses.addApiResponse("504", failure("Pokemon provider timed out"));
                }
                responses.values().forEach(response -> response.addHeaderObject("X-Request-ID",
                        new Header().description("Correlation ID").schema(new StringSchema())));
            }));
        };
    }

    private static ApiResponse failure(String description) {
        return new ApiResponse().description(description).content(new Content().addMediaType("application/json",
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiError"))));
    }
}
