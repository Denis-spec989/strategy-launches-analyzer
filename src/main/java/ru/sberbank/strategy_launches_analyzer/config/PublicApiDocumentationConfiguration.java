package ru.sberbank.strategy_launches_analyzer.config;

import com.fasterxml.jackson.databind.node.MissingNode;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

@Configuration
public class PublicApiDocumentationConfiguration {
    @Bean
    public OpenAPI publicApiOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Strategy Launches Analyzer API")
                        .version("1.1.0"))
                .servers(List.of(new Server().url("/")));
    }

    @Bean
    public OpenApiCustomizer removeSyntheticSchemaDefaults() {
        return openApi -> {
            Set<Schema<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            if (openApi.getComponents() != null && openApi.getComponents().getSchemas() != null) {
                openApi.getComponents().getSchemas().values()
                        .forEach(schema -> sanitizeSchema(schema, visited));
            }
            if (openApi.getPaths() != null) {
                openApi.getPaths().values().stream()
                        .flatMap(path -> path.readOperations().stream())
                        .forEach(operation -> sanitizeOperation(operation, visited));
            }
        };
    }

    private static void sanitizeOperation(Operation operation, Set<Schema<?>> visited) {
        if (operation.getRequestBody() != null) {
            sanitizeContent(operation.getRequestBody().getContent(), visited);
        }
        if (operation.getResponses() != null) {
            operation.getResponses().values()
                    .forEach(response -> sanitizeContent(response.getContent(), visited));
        }
        if (operation.getParameters() != null) {
            operation.getParameters().forEach(parameter -> sanitizeSchema(parameter.getSchema(), visited));
        }
    }

    private static void sanitizeContent(Content content, Set<Schema<?>> visited) {
        if (content != null) {
            content.values().forEach(mediaType -> sanitizeSchema(mediaType.getSchema(), visited));
        }
    }

    private static void sanitizeSchema(Schema<?> schema, Set<Schema<?>> visited) {
        if (schema == null || !visited.add(schema)) {
            return;
        }
        Object defaultValue = schema.getDefault();
        if (isSyntheticDefault(defaultValue)) {
            schema.setDefault(null);
            schema.setDefaultSetFlag(false);
        }
        if (schema.getJsonSchema() != null
                && schema.getJsonSchema().containsKey("default")
                && isSyntheticDefault(schema.getJsonSchema().get("default"))) {
            schema.getJsonSchema().remove("default");
        }
        if (schema.getProperties() != null) {
            schema.getProperties().values().forEach(property -> sanitizeSchema(property, visited));
        }
        sanitizeSchema(schema.getItems(), visited);
        sanitizeSchemas(schema.getAllOf(), visited);
        sanitizeSchemas(schema.getAnyOf(), visited);
        sanitizeSchemas(schema.getOneOf(), visited);
        sanitizeSchema(schema.getNot(), visited);

        if (schema.getAdditionalProperties() instanceof Schema<?> additionalProperties) {
            sanitizeSchema(additionalProperties, visited);
            if (schema.get$ref() != null && isEmptySchema(additionalProperties)) {
                schema.setAdditionalProperties(null);
            }
        }
    }

    private static void sanitizeSchemas(List<Schema> schemas, Set<Schema<?>> visited) {
        if (schemas != null) {
            schemas.forEach(schema -> sanitizeSchema(schema, visited));
        }
    }

    private static boolean isSyntheticDefault(Object value) {
        return value == null || value instanceof MissingNode || "".equals(value);
    }

    private static boolean isEmptySchema(Schema<?> schema) {
        return schema.get$ref() == null
                && schema.getType() == null
                && schema.getFormat() == null
                && schema.getPattern() == null
                && schema.getDescription() == null
                && schema.getProperties() == null
                && schema.getItems() == null
                && schema.getAllOf() == null
                && schema.getAnyOf() == null
                && schema.getOneOf() == null
                && schema.getNot() == null
                && schema.getEnum() == null;
    }
}
