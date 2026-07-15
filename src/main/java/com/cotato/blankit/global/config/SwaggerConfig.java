package com.cotato.blankit.global.config;

import com.cotato.blankit.global.config.swagger.NotImplementedYet;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class SwaggerConfig {

    private static final Map<String, String> ERROR_EXAMPLES = Map.of(
            "400", "{\"code\":\"INVALID_INPUT\",\"message\":\"잘못된 입력값입니다.\"}",
            "401", "{\"code\":\"UNAUTHORIZED\",\"message\":\"인증이 필요합니다.\"}",
            "403", "{\"code\":\"FORBIDDEN\",\"message\":\"접근 권한이 없습니다.\"}",
            "404", "{\"code\":\"NOT_FOUND\",\"message\":\"요청한 리소스를 찾을 수 없습니다.\"}"
    );

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .info(new Info()
                        .title("Blankit API")
                        .description("Blankit 백엔드 API 문서")
                        .version("v1.0.0"));
    }

    @Bean
    public OperationCustomizer errorResponseCustomizer() {
        return (operation, handlerMethod) -> {
            if (operation == null || operation.getResponses() == null) return operation;
            operation.getResponses().forEach((statusCode, apiResponse) -> {
                String exampleJson = ERROR_EXAMPLES.get(statusCode);
                if (exampleJson != null) {
                    Content content = new Content();
                    MediaType mediaType = new MediaType();
                    mediaType.addExamples("example", new Example().value(exampleJson));
                    content.addMediaType("application/json", mediaType);
                    apiResponse.setContent(content);
                }
            });
            return operation;
        };
    }

    @Bean
    public GroupedOpenApi allApi() {
        return GroupedOpenApi.builder()
                .group("0. 전체")
                .pathsToMatch("/api/**")
                .build();
    }

    @Bean
    public GroupedOpenApi implementedApi() {
        return GroupedOpenApi.builder()
                .group("1. 구현 완료")
                .pathsToMatch("/api/**")
                .addOperationCustomizer((operation, handlerMethod) -> {
                    if (handlerMethod.getBeanType().isAnnotationPresent(NotImplementedYet.class)) {
                        return null;
                    }
                    return operation;
                })
                .build();
    }

    @Bean
    public GroupedOpenApi mockApi() {
        return GroupedOpenApi.builder()
                .group("2. 명세만 (mock)")
                .pathsToMatch("/api/**")
                .addOperationCustomizer((operation, handlerMethod) -> {
                    if (!handlerMethod.getBeanType().isAnnotationPresent(NotImplementedYet.class)) {
                        return null;
                    }
                    return operation;
                })
                .build();
    }
}
