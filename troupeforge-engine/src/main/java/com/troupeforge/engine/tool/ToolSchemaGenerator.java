package com.troupeforge.engine.tool;

import com.troupeforge.core.tool.ToolParam;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates a JSON schema {@code Map<String, Object>} from a {@link Record} class
 * by reflecting on its components and {@link ToolParam} annotations.
 */
public final class ToolSchemaGenerator {

    private ToolSchemaGenerator() {}

    public static Map<String, Object> generateSchema(Class<? extends Record> recordClass) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (RecordComponent component : recordClass.getRecordComponents()) {
            String name = component.getName();
            Map<String, Object> prop = toPropertySchema(component);
            properties.put(name, prop);

            ToolParam annotation = component.getAnnotation(ToolParam.class);
            if (annotation == null || annotation.required()) {
                required.add(name);
            }
        }

        schema.put("properties", properties);
        schema.put("required", required);
        return schema;
    }

    private static Map<String, Object> toPropertySchema(RecordComponent component) {
        Map<String, Object> prop = new LinkedHashMap<>();
        Class<?> type = component.getType();

        if (type == String.class) {
            prop.put("type", "string");
        } else if (type == int.class || type == Integer.class || type == long.class || type == Long.class) {
            prop.put("type", "integer");
        } else if (type == double.class || type == Double.class || type == float.class || type == Float.class) {
            prop.put("type", "number");
        } else if (type == boolean.class || type == Boolean.class) {
            prop.put("type", "boolean");
        } else if (type == List.class) {
            prop.put("type", "array");
            prop.put("items", Map.of("type", "string"));
        } else if (type.isRecord()) {
            @SuppressWarnings("unchecked")
            var nested = (Class<? extends Record>) type;
            prop.putAll(generateSchema(nested));
        } else {
            prop.put("type", "string");
        }

        ToolParam annotation = component.getAnnotation(ToolParam.class);
        if (annotation != null && !annotation.description().isEmpty()) {
            prop.put("description", annotation.description());
        }

        return prop;
    }
}
