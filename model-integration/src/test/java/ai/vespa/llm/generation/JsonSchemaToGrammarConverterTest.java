package ai.vespa.llm.generation;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


public class JsonSchemaToGrammarConverterTest {

    /**
     * Test a simple object schema with two properties (string, integer).
     */
    @Test
    public void testSimpleObjectSchema() {
        String schema = """
        {
          "type": "object",
          "properties": {
            "name":  { "type": "string" },
            "age":   { "type": "integer" }
          },
          "required": ["name"]
        }
        """;

        String grammar = JsonSchemaToGrammarConverter.convert(
                schema,
                false,                      // allowFetch
                false,                      // dotAll
                false,                      // rawPattern
                new HashMap<>()            // propOrder
        );

        // Basic checks
        Assertions.assertNotNull(grammar, "Grammar should not be null");
        Assertions.assertFalse(grammar.isBlank(), "Grammar should not be blank");

        // We expect the grammar to contain rules for 'object', 'string', 'integer', etc.
        Assertions.assertTrue(
                grammar.contains("object ::= "),
                "Grammar should contain a rule for 'object'"
        );
        Assertions.assertTrue(
                grammar.contains("name-kv ::= \"\\\"name\\\"\" space"),
                "Grammar should define a property KV rule for 'name'"
        );
        Assertions.assertTrue(
                grammar.contains("age-kv ::= \"\\\"age\\\"\" space"),
                "Grammar should define a property KV rule for 'age'"
        );
    }

    /**
     * Test an array schema with minItems and maxItems constraints.
     */
    @Test
    public void testArraySchema() {
        String schema = """
        {
          "type": "array",
          "items": { "type": "string" },
          "minItems": 1,
          "maxItems": 3
        }
        """;

        String grammar = JsonSchemaToGrammarConverter.convert(
                schema,
                false,
                false,
                false,
                Collections.emptyMap()
        );

        Assertions.assertNotNull(grammar);
        Assertions.assertFalse(grammar.isBlank());

        // Expect repetition with {1,3}
        Assertions.assertTrue(
                grammar.contains("string{1,3}"),
                "Should see a repetition block for 1..3 string items"
        );
    }

    /**
     * Test an enum schema.
     */
    @Test
    public void testEnumSchema() {
        String schema = """
        {
          "enum": ["red", "green", "blue"]
        }
        """;

        String grammar = JsonSchemaToGrammarConverter.convert(
                schema,
                false,
                false,
                false,
                Collections.emptyMap()
        );

        Assertions.assertNotNull(grammar);
        Assertions.assertFalse(grammar.isBlank());

        // The grammar should contain something like: ( "red" | "green" | "blue" ) space
        Assertions.assertTrue(
                grammar.contains("( \"\\\"red\\\"\" | \"\\\"green\\\"\" | \"\\\"blue\\\"\" ) space"),
                "Grammar should produce an enum union with 'red', 'green', 'blue'"
        );
    }

    /**
     * Test a string pattern schema.
     */
    @Test
    public void testStringPatternSchema() {
        String schema = """
        {
          "type": "string",
          "pattern": "^hello\\\\sworld$"
        }
        """;

        // We disable dotAll, rawPattern => the pattern will be wrapped in quotes
        String grammar = JsonSchemaToGrammarConverter.convert(
                schema,
                false,
                false,
                false,
                Collections.emptyMap()
        );

        Assertions.assertNotNull(grammar);
        Assertions.assertFalse(grammar.isBlank());

        // Check that "hello\\sworld" or a transformation of it appears
        // The pattern will appear inside something like:
        //    root ::= "\"\" (hello...) "\"\" space
        // or a sub-rule. Let's just confirm "hello" or "world" is in there.
        Assertions.assertTrue(
                grammar.contains("hello") && grammar.contains("world"),
                "Grammar should embed 'hello' and 'world' in the pattern rule"
        );
    }

    /**
     * Test a property order usage (just to ensure it's not broken).
     */
    @Test
    public void testPropOrder() {
        String schema = """
        {
          "type": "object",
          "properties": {
            "zprop": { "type": "string" },
            "aprop": { "type": "number" }
          }
        }
        """;

        // Suppose we want "aprop" to appear before "zprop"
        Map<String, Integer> propOrder = new HashMap<>();
        propOrder.put("aprop", 0);

        String grammar = JsonSchemaToGrammarConverter.convert(
                schema,
                false,
                false,
                false,
                propOrder
        );

        Assertions.assertNotNull(grammar);
        Assertions.assertFalse(grammar.isBlank());

        // We expect the "aprop" KV rule to appear earlier than "zprop"
        // There's no trivial direct string check for order, but we can do a small substring check:
        int idxA = grammar.indexOf("aprop-kv");
        int idxZ = grammar.indexOf("zprop-kv");

        Assertions.assertTrue(idxA >= 0 && idxZ >= 0, "Should define both properties");
        Assertions.assertTrue(idxA < idxZ, "Aprop should appear before Zprop in the grammar output");
    }
}
