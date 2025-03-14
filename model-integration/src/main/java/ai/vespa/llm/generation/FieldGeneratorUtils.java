package ai.vespa.llm.generation;

import ai.vespa.json.Json;
import com.fasterxml.jackson.core.JsonFactory;
import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.Field;
import com.yahoo.document.MapDataType;
import com.yahoo.document.StructDataType;
import com.yahoo.document.WeightedSetDataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.json.DocumentOperationType;
import com.yahoo.document.json.JsonReader;
import com.yahoo.document.json.ParsedDocumentOperation;
import com.yahoo.text.Utf8;

import java.io.ByteArrayInputStream;

/**
 * Helper methods for using LLM structured output to generate document field values.
 * 
 * @author glebashnik
 */
public class FieldGeneratorUtils {
    /**
     * Generates JSON schema for a document field based on it's path and data type that corresponds 
     * to a JSON format {"[fieldPath]": fieldValue} where [fieldPath] is [document type name].[field name].
     */
    public static String generateJsonSchemaForField(String fieldPath, DataType fieldType) {
        var schema = Json.Builder.newObject()
                .set("type", "object");

        var properties = schema.setObject("properties");
        Json.Builder.Object fieldValue;
        
        try {
            fieldValue = generateJsonSchemaForFieldValue(fieldType);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Failed to generate schema for field %s of type %s"
                    .formatted(fieldPath, fieldType.getName()), e);
        }
        
        properties.set(fieldPath, fieldValue);

        var required = schema.setArray("required");
        required.add(fieldPath);
        schema.set("additionalProperties", false);

        return schema.build().toJson(true);
    }
    
    /**
     * Generates JSON schema for a document field value.
     * Schemas are compatible with JSON format used for feeding.
     * Not all types are suppoerted. 
     * See conditions in code for supported types.
     */
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
            field.set("type", "object");
            var value = generateJsonSchemaForFieldValue(mapType.getValueType());
            field.set("additionalProperties", value);
        } else if (fieldType instanceof WeightedSetDataType) {
            field.set("type", "object");
            var additionalProperties = field.setObject("additionalProperties");
            additionalProperties.set("type", "integer");    
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
            throw new IllegalArgumentException("Failed to generate schema for field type %s".formatted(fieldType));
        }

        return field;
    }

    /**
     * Parses JSON containing document field value.
     * Uses the same parser as the one used during feeding.
     */
    public static FieldValue parseJsonFieldValue(String jsonFieldValue, String fieldPath, DataType fieldType) {
        // Create a dummy document operation to use JSON document parser API.
        // API for parsing individual fields is not exposed.
        var documentTypeName = "dummy"; // Can't use built-in type name "document".
        var documentId = "id:generate:%s::0".formatted(documentTypeName);
        var jsonDocumentOperation = """
                {
                    "put": "%s",
                    "fields": %s
                }
                """.formatted(documentId, jsonFieldValue);
        
        // Create and register types
        var types = new DocumentTypeManager();
        var docType = new DocumentType(documentTypeName);
        var field = new Field(fieldPath, fieldType);
        docType.addField(field);
        types.registerDocumentType(docType);
        
        // Parse JSON document
        var parserFactory = new JsonFactory();
        var input = new ByteArrayInputStream(Utf8.toBytes(jsonDocumentOperation));
        var jsonReader = new JsonReader(types, input, parserFactory);
        ParsedDocumentOperation parsedDocumentOperation;
        
        try {
            parsedDocumentOperation = jsonReader.readSingleDocumentStreaming(DocumentOperationType.PUT, documentId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Failed to parse JSON value for field %s of type %s: %s"
                            .formatted(fieldPath, fieldType.getName(), jsonFieldValue), e);
        }
        
        var documentOperation = (DocumentPut) parsedDocumentOperation.operation();
        var document = documentOperation.getDocument();
        return document.getFieldValue(field);
    }
}
