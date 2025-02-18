package ai.vespa.llm.generation;

import com.yahoo.document.DataType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JsonSchemaUtilsTest {
    @Test
    void testGenerateJsonSchemaForDocumentField() {
        var schema = JsonSchemaUtils.generateJsonSchemaForDocumentField("title", DataType.STRING);
        var expectedSchema = """
                {
                  "type": "object",
                  "properties": {
                    "title": {
                      "type": "string"
                    }
                  },
                  "required": [
                    "title"
                  ],
                  "additionalProperties": false
                }
                """;
        assertEquals(expectedSchema, schema);
    }
}
