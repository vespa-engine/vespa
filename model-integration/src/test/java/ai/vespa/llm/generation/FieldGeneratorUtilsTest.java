package ai.vespa.llm.generation;

import ai.vespa.json.Json;
import com.fasterxml.jackson.core.JsonFactory;
import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.Field;
import com.yahoo.document.PrimitiveDataType;
import com.yahoo.document.StructDataType;
import com.yahoo.document.datatypes.BoolFieldValue;
import com.yahoo.document.datatypes.ByteFieldValue;
import com.yahoo.document.datatypes.DoubleFieldValue;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.Float16FieldValue;
import com.yahoo.document.datatypes.FloatFieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.LongFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.json.JsonReader;
import com.yahoo.document.json.readers.DocumentParseInfo;
import com.yahoo.document.json.readers.VespaJsonDocumentReader;
import com.yahoo.document.serialization.DocumentDeserializer;
import com.yahoo.document.serialization.DocumentDeserializerFactory;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.tensor.TensorType;
import com.yahoo.text.JSON;
import com.yahoo.text.Utf8;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FieldGeneratorUtilsTest {

    static Stream<Arguments> providePrimitiveTypes() {
        return Stream.of(
                Arguments.of(PrimitiveDataType.STRING, "string", new StringFieldValue("Polly")),
                Arguments.of(PrimitiveDataType.INT, "integer", new IntegerFieldValue(42)),
                Arguments.of(PrimitiveDataType.LONG, "integer", new LongFieldValue(42)),
                Arguments.of(PrimitiveDataType.BYTE, "integer", new ByteFieldValue(42)),
                Arguments.of(PrimitiveDataType.FLOAT, "number", new FloatFieldValue(123.4567f)),
                Arguments.of(PrimitiveDataType.DOUBLE, "number", new DoubleFieldValue(123.4567)),
                Arguments.of(PrimitiveDataType.FLOAT16, "number", new Float16FieldValue(123.4567f)),
                Arguments.of(PrimitiveDataType.BOOL, "boolean", new BoolFieldValue(false))
        );
    }
    
    @ParameterizedTest
    @MethodSource("providePrimitiveTypes")
    void testGenerateJsonSchemaForPrimitiveField(PrimitiveDataType primitiveType, String jsonType, FieldValue value) {
        var schema = FieldGeneratorUtils.generateJsonSchemaForField("doc.field", primitiveType);
        var expectedSchema = """
                {
                  "type": "object",
                  "properties": {
                    "doc.field": {
                      "type": "%s"
                    }
                  },
                  "required": [
                    "doc.field"
                  ],
                  "additionalProperties": false
                }
                """.formatted(jsonType);
        assertEquals(expectedSchema, schema);
    }

    @ParameterizedTest
    @MethodSource("providePrimitiveTypes")
    void testGenerateJsonSchemaForArrayField(PrimitiveDataType primitiveType, String jsonType, FieldValue value) {
        var schema = FieldGeneratorUtils.generateJsonSchemaForField("doc.field", DataType.getArray(primitiveType));
        var expectedSchema = """
                {
                  "type": "object",
                  "properties": {
                    "doc.field": {
                      "type": "array",
                      "items": {
                        "type": "%s"
                      }
                    }
                  },
                  "required": [
                    "doc.field"
                  ],
                  "additionalProperties": false
                }
                """.formatted(jsonType);
        assertEquals(expectedSchema, schema);
    }
    
    @Test
    void testGenerateJsonSchemaForStructField() {
        StructDataType structType = new StructDataType("person");
        structType.addField(new com.yahoo.document.Field("name", DataType.STRING));
        structType.addField(new com.yahoo.document.Field("age", DataType.INT));
        
        var schema = FieldGeneratorUtils.generateJsonSchemaForField("doc.field", structType);
        var expectedSchema = """
                {
                  "type": "object",
                  "properties": {
                    "doc.field": {
                      "type": "object",
                      "properties": {
                        "name": {
                          "type": "string"
                        },
                        "age": {
                          "type": "integer"
                        }
                      },
                      "required": [
                        "name",
                        "age"
                      ],
                      "additionalProperties": false
                    }
                  },
                  "required": [
                    "doc.field"
                  ],
                  "additionalProperties": false
                }
                """;
        assertEquals(expectedSchema, schema);
    }
    
    @Test
    void testGenerateJsonSchemaForMapField() {
        var schema = FieldGeneratorUtils.generateJsonSchemaForField("doc.field", DataType.getMap(DataType.STRING, DataType.INT));
        
        var expectedSchema = """
                {
                  "type": "object",
                  "properties": {
                    "doc.field": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "properties": {
                          "key": {
                            "type": "string"
                          },
                          "value": {
                            "type": "integer"
                          }
                        },
                        "required": [
                          "key",
                          "value"
                        ],
                        "additionalProperties": false
                      }
                    }
                  },
                  "required": [
                    "doc.field"
                  ],
                  "additionalProperties": false
                }
                """;
        assertEquals(expectedSchema, schema);
    }

    @ParameterizedTest
    @MethodSource("providePrimitiveTypes")
    void testParseJsonToPrimitiveFieldValue(PrimitiveDataType primitiveType, String jsonType, FieldValue value) throws IOException {
        var jsonValue = primitiveType.equals(DataType.STRING) ? "\"" + value + "\"" : value.toString();
        
        var jsonDoc = """
                {
                    "put": "id:dummy:dummy::dummy",
                    "fields": {
                        "doc.field": %s
                    }
                }
                """.formatted(jsonValue);
        
        
        var types = new DocumentTypeManager();
        var docType = new DocumentType("dummy");
        var field = new Field("doc.field", primitiveType);
        docType.addField(field);
        types.registerDocumentType(docType);
        
        var parserFactory = new JsonFactory();
        var input = new ByteArrayInputStream(Utf8.toBytes(jsonDoc));
        var jsonReader = new JsonReader(types, input, parserFactory);
        var parseInfo = jsonReader.parseDocument().get();
        var put = new DocumentPut(new Document(docType, parseInfo.documentId));
        new VespaJsonDocumentReader(false).readPut(parseInfo.fieldsBuffer, put);
        var doc = put.getDocument();
        var fieldValue = doc.getFieldValue(field);

        assertEquals(fieldValue, value);
    }
}
