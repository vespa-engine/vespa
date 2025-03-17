package ai.vespa.llm.generation;

import com.yahoo.document.DataType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LanguageModelUtilsTest {
    @Test
    void testGenerateJsonSchemaForStringField() {
        var schema = LanguageModelUtils.generateJsonSchemaForField("summary", DataType.STRING);
        var expectedSchema = """
                {
                  "type": "object",
                  "properties": {
                    "summary": {
                      "type": "string"
                    }
                  },
                  "required": [
                    "summary"
                  ],
                  "additionalProperties": false
                }
                """;
        assertEquals(expectedSchema, schema);
    }

    @Test
    void testGenerateJsonSchemaForStringArrayField() {
        var schema = LanguageModelUtils.generateJsonSchemaForField("names", DataType.getArray(DataType.STRING));
        var expectedSchema = """
                {
                  "type": "object",
                  "properties": {
                    "names": {
                      "type": "array",
                      "items": {
                        "type": "string"
                      }
                    }
                  },
                  "required": [
                    "names"
                  ],
                  "additionalProperties": false
                }
                """;
        assertEquals(expectedSchema, schema);
    }
}
