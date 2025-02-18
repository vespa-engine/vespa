package ai.vespa.llm.generation;

import ai.vespa.json.Json;
import com.yahoo.document.DataType;

public class JsonSchemaUtils {
    public static String generateJsonSchemaForDocumentField(String fieldName, DataType fieldType) {
        var schema = Json.Builder.newObject()
                .set("type", "object");
        
        var properties = schema.setObject("properties");
        var field = properties.setObject(fieldName);

        if (fieldType.equals(DataType.STRING)) {
            field.set("type", "string");
        } if (fieldType.equals(DataType.INT)) {
            field.set("type", "integer");
        } else if (fieldType.equals(DataType.DOUBLE)) {
            field.set("type", "double");
        } else if (fieldType.equals(DataType.FLOAT)) {
            field.set("type", "float");
        } else if (fieldType.equals(DataType.BOOL)) {
            field.set("type", "boolean");
        } else if (fieldType.equals(DataType.STRING)) {
            field.set("type", "string");
        } else {
            throw new IllegalArgumentException("Unsupported field type: " + fieldType);
        }
        
        var required = schema.setArray("required");
        required.add(fieldName);
        schema.set("additionalProperties", false);
        
        return schema.build().toJson(true);
    }
}
