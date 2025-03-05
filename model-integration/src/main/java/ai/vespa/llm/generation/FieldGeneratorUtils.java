package ai.vespa.llm.generation;

import ai.vespa.json.Json;
import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.MapDataType;
import com.yahoo.document.StructDataType;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StringFieldValue;

public class FieldGeneratorUtils {
    public static String generateJsonSchemaForField(String fieldPath, DataType fieldType) {
        var schema = Json.Builder.newObject()
                .set("type", "object");

        var properties = schema.setObject("properties");
        var fieldValue = generateJsonSchemaForFieldValue(fieldType);
        properties.set(fieldPath, fieldValue);

        var required = schema.setArray("required");
        required.add(fieldPath);
        schema.set("additionalProperties", false);

        return schema.build().toJson(true);
    }

    private static Json.Builder.Object generateJsonSchemaForFieldValue(DataType fieldType) {
        var field = Json.Builder.newObject();

        if (fieldType.equals(DataType.BOOL)) {
            field.set("type", "boolean");
        } else if (fieldType.equals(DataType.STRING)) {
            field.set("type", "string");
        } else if (fieldType.equals(DataType.INT) || fieldType.equals(DataType.LONG) || fieldType.equals(
                DataType.BYTE)) {
            field.set("type", "integer");
        } else if (fieldType.equals(DataType.FLOAT) || fieldType.equals(DataType.DOUBLE) || fieldType.equals(
                DataType.FLOAT16)) {
            field.set("type", "number");
        } else if (fieldType instanceof ArrayDataType arrayType) {
            field.set("type", "array");
            var itemsValue = generateJsonSchemaForFieldValue(arrayType.getNestedType());
            field.set("items", itemsValue);
        } else if (fieldType instanceof MapDataType mapType) {
            field.set("type", "array");
            
            var items = field.setObject("items");
            items.set("type", "object");
            var properties = items.setObject("properties");

            var keyValue =  generateJsonSchemaForFieldValue(mapType.getKeyType());
            properties.set("key", keyValue);

            var valueValue = generateJsonSchemaForFieldValue(mapType.getValueType());
            properties.set("value", valueValue);

            items.setArray("required").add("key").add("value");
            items.set("additionalProperties", false);
        } else if (fieldType instanceof StructDataType structType) {
            field.set("type", "object");
            var properties = field.setObject("properties");
            var required = field.setArray("required");
            field.set("additionalProperties", false);

            for (var structField : structType.getFields()) {
                var fieldName = structField.getName();
                var fieldValue = generateJsonSchemaForFieldValue(structField.getDataType());
                properties.set(fieldName, fieldValue);
                required.add(fieldName);
            }
        } else {
            throw new IllegalArgumentException("Unsupported field type: " + fieldType);
        }

        return field;
    }

    public static FieldValue parseJsonToFieldValue(String jsonText, String fieldPath, DataType fieldType) {
        var jsonObject = Json.of(jsonText);
        var jsonField = jsonObject.field(fieldPath);

        if (fieldType.equals(DataType.STRING) && jsonField.isString()) {
            return new StringFieldValue(jsonField.asString());
        }

        if (fieldType.equals(DataType.getArray(DataType.STRING)) && jsonField.isString()) {
            var arrayFieldValue = new Array<>(DataType.getArray(DataType.STRING));

            for (var jsonItem : jsonField) {
                arrayFieldValue.add(new StringFieldValue(jsonItem.asString()));
            }

            return arrayFieldValue;
        }

        throw new IllegalArgumentException(
                "Can't parse the following generated text as %s: %s".formatted(fieldType.getName(), jsonText));
    }
}
