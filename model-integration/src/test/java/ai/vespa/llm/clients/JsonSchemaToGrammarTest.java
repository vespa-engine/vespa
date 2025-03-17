package ai.vespa.llm.clients;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;


/**
 * It is a partial port of C++ unit tests from llama.cpp repository.
 */
public class JsonSchemaToGrammarTest {
    @Test
    public void testMin0() {
        var schema = """
                {
                    "type": "integer",
                    "minimum": 0
                }
                """;
        var expectedGrammar = """
                root ::= ([0] | [1-9] [0-9]{0,15}) space
                space ::= | " " | "\\n" [ \\t]{0,20}
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testMin1() {
        var schema = """
                {
                    "type": "integer",
                    "minimum": 1
                }
                """;
        var expectedGrammar = """
                root ::= ([1-9] [0-9]{0,15}) space
                space ::= | " " | "\\n" [ \\t]{0,20}
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testMin3() {
        var schema = """
                {
                    "type": "integer",
                    "minimum": 3
                }
                """;
        var expectedGrammar = """
                root ::= ([1-2] [0-9]{1,15} | [3-9] [0-9]{0,15}) space
                space ::= | " " | "\\n" [ \\t]{0,20}
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testMin9() {
        var schema = """
                {
                    "type": "integer",
                    "minimum": 9
                }
                """;
        var expectedGrammar = """
                root ::= ([1-8] [0-9]{1,15} | [9] [0-9]{0,15}) space
                space ::= | " " | "\\n" [ \\t]{0,20}
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testMin10() {
        var schema = """
                {
                    "type": "integer",
                    "minimum": 10
                }
                """;
        var expectedGrammar = """
                root ::= ([1] ([0-9]{1,15}) | [2-9] [0-9]{1,15}) space
                space ::= | " " | "\\n" [ \\t]{0,20}
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testMin25() {
        var schema = """
                {
                    "type": "integer",
                    "minimum": 25
                }
                """;
        var expectedGrammar = """
                root ::= ([1] [0-9]{2,15} | [2] ([0-4] [0-9]{1,14} | [5-9] [0-9]{0,14}) | [3-9] [0-9]{1,15}) space
                space ::= | " " | "\\n" [ \\t]{0,20}
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testMax30() {
        var schema = """
                {
                    "type": "integer",
                    "maximum": 30
                }
                """;
        var expectedGrammar = """
                root ::= ("-" [1-9] [0-9]{0,15} | [0-9] | ([1-2] [0-9] | [3] [0])) space
                space ::= | " " | "\\n" [ \\t]{0,20}
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testMinNeg5() {
        var schema = """
                {
                    "type": "integer",
                    "minimum": -5
                }
                """;
        var expectedGrammar = """
                root ::= ("-" ([0-5]) | [0] | [1-9] [0-9]{0,15}) space
                space ::= | " " | "\\n" [ \\t]{0,20}
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testMinNeg123() {
        var schema = """
                {
                    "type": "integer",
                    "minimum": -123
                }
                """;
        var expectedGrammar = """
                root ::= ("-" ([0-9] | ([1-8] [0-9] | [9] [0-9]) | [1] ([0-1] [0-9] | [2] [0-3])) | [0] | [1-9] [0-9]{0,15}) space
                space ::= | " " | "\\n" [ \\t]{0,20}
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testMaxNeg5() {
        var schema = """
                {
                    "type": "integer",
                    "maximum": -5
                }
                """;
        var expectedGrammar = """
                root ::= ("-" ([0-4] [0-9]{1,15} | [5-9] [0-9]{0,15})) space
                space ::= | " " | "\\n" [ \\t]{0,20}
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testMax1() {
        var schema = """
                {
                    "type": "integer",
                    "maximum": 1
                }
                """;
        var expectedGrammar = """
                root ::= ("-" [1-9] [0-9]{0,15} | [0-1]) space
                space ::= | " " | "\\n" [ \\t]{0,20}
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testMax100() {
        var schema = """
                {
                    "type": "integer",
                    "maximum": 100
                }
                """;
        var expectedGrammar = """
                root ::= ("-" [1-9] [0-9]{0,15} | [0-9] | ([1-8] [0-9] | [9] [0-9]) | "100") space
                space ::= | " " | "\\n" [ \\t]{0,20}
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testMin0Max23() {
        var schema = """
                {
                    "type": "integer",
                    "minimum": 0,
                    "maximum": 23
                }
                """;
        var expectedGrammar = """
                root ::= ([0-9] | ([1] [0-9] | [2] [0-3])) space
                space ::= | " " | "\\n" [ \\t]{0,20}
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testMin15Max300() {
        var schema = """
                {
                    "type": "integer",
                    "minimum": 15,
                    "maximum": 300
                }
                """;
        var expectedGrammar = """
                root ::= (([1] ([5-9]) | [2-9] [0-9]) | ([1-2] [0-9]{2} | [3] "00")) space
                space ::= | " " | "\\n" [ \\t]{0,20}
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testMin5Max30() {
        var schema = """
                {
                    "type": "integer",
                    "minimum": 5,
                    "maximum": 30
                }
                """;
        var expectedGrammar = """
                root ::= ([5-9] | ([1-2] [0-9] | [3] [0])) space
                space ::= | " " | "\\n" [ \\t]{0,20}
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testMinNeg123Max42() {
        var schema = """
                {
                    "type": "integer",
                    "minimum": -123,
                    "maximum": 42
                }
                """;
        var expectedGrammar = """
                root ::= ("-" ([0-9] | ([1-8] [0-9] | [9] [0-9]) | [1] ([0-1] [0-9] | [2] [0-3])) | [0-9] | ([1-3] [0-9] | [4] [0-2])) space
                space ::= | " " | "\\n" [ \\t]{0,20}
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testMinNeg10Max10() {
        var schema = """
                {
                    "type": "integer",
                    "minimum": -10,
                    "maximum": 10
                }
                """;
        var expectedGrammar = """
                root ::= ("-" ([0-9] | "10") | [0-9] | "10") space
                space ::= | " " | "\\n" [ \\t]{0,20}
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testUnknownType() {
        var schema = """
                {
                    "type": "kaboom"
                }
                """;

        assertThrows(RuntimeException.class, () -> {
            JsonSchemaToGrammar.convert(schema);
        });
    }

    @Test
    public void testInvalidType() {
        var schema = """
                {
                    "type": 123
                }
                """;
        assertThrows(RuntimeException.class, () -> {
            JsonSchemaToGrammar.convert(schema);
        });
    }

    @Test
    public void testEmptySchemaObject() {
        var schema = """
                {}
                """;
        var expectedGrammar = """
                array ::= "[" space ( value ("," space value)* )? "]" space
                boolean ::= ("true" | "false") space
                char ::= [^"\\\\\\x7F\\x00-\\x1F] | [\\\\] (["\\\\bfnrt] | "u" [0-9a-fA-F]{4})
                decimal-part ::= [0-9]{1,16}
                integral-part ::= [0] | [1-9] [0-9]{0,15}
                null ::= "null" space
                number ::= ("-"? integral-part) ("." decimal-part)? ([eE] [-+]? integral-part)? space
                object ::= "{" space ( string ":" space value ("," space string ":" space value)* )? "}" space
                root ::= object
                space ::= | " " | "\\n" [ \\t]{0,20}
                string ::= "\\"" char* "\\"" space
                value ::= object | array | string | number | boolean | null
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testExoticFormats() {
        var schema = """
                {
                    "items": [
                        { "format": "date" },
                        { "format": "uuid" },
                        { "format": "time" },
                        { "format": "date-time" }
                    ]
                }
                """;
        var expectedGrammar = """
                date ::= [0-9]{4} "-" ( [0] [1-9] | [1] [0-2] ) "-" ( [0] [1-9] | [1-2] [0-9] | [3] [0-1] )
                date-string ::= "\\"" date "\\"" space
                date-time ::= date "T" time
                date-time-string ::= "\\"" date-time "\\"" space
                root ::= "[" space tuple-0 "," space uuid "," space tuple-2 "," space tuple-3 "]" space
                space ::= | " " | "\\n" [ \\t]{0,20}
                time ::= ([01] [0-9] | [2] [0-3]) ":" [0-5] [0-9] ":" [0-5] [0-9] ( "." [0-9]{3} )? ( "Z" | ( "+" | "-" ) ( [01] [0-9] | [2] [0-3] ) ":" [0-5] [0-9] )
                time-string ::= "\\"" time "\\"" space
                tuple-0 ::= date-string
                tuple-2 ::= time-string
                tuple-3 ::= date-time-string
                uuid ::= "\\"" [0-9a-fA-F]{8} "-" [0-9a-fA-F]{4} "-" [0-9a-fA-F]{4} "-" [0-9a-fA-F]{4} "-" [0-9a-fA-F]{12} "\\"" space
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testString() {
        var schema = """
                {
                    "type": "string"
                }
                """;
        var expectedGrammar = """
                char ::= [^"\\\\\\x7F\\x00-\\x1F] | [\\\\] (["\\\\bfnrt] | "u" [0-9a-fA-F]{4})
                root ::= "\\"" char* "\\"" space
                space ::= | " " | "\\n" [ \\t]{0,20}
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testStringWithMinLength1() {
        var schema = """
                {
                    "type": "string",
                    "minLength": 1
                }
                """;
        var expectedGrammar = """
                char ::= [^"\\\\\\x7F\\x00-\\x1F] | [\\\\] (["\\\\bfnrt] | "u" [0-9a-fA-F]{4})
                root ::= "\\"" char+ "\\"" space
                space ::= | " " | "\\n" [ \\t]{0,20}
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testStringWithMinLength3() {
        var schema = """
                {
                    "type": "string",
                    "minLength": 3
                }
                """;
        var expectedGrammar = """
                char ::= [^"\\\\\\x7F\\x00-\\x1F] | [\\\\] (["\\\\bfnrt] | "u" [0-9a-fA-F]{4})
                root ::= "\\"" char{3,} "\\"" space
                space ::= | " " | "\\n" [ \\t]{0,20}
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testStringWithMaxLength() {
        var schema = """
                {
                    "type": "string",
                    "maxLength": 3
                }
                """;
        var expectedGrammar = """
                char ::= [^"\\\\\\x7F\\x00-\\x1F] | [\\\\] (["\\\\bfnrt] | "u" [0-9a-fA-F]{4})
                root ::= "\\"" char{0,3} "\\"" space
                space ::= | " " | "\\n" [ \\t]{0,20}
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testStringWithMinAndMaxLength() {
        var schema = """
                {
                    "type": "string",
                    "minLength": 1,
                    "maxLength": 4
                }
                """;
        var expectedGrammar = """
                char ::= [^"\\\\\\x7F\\x00-\\x1F] | [\\\\] (["\\\\bfnrt] | "u" [0-9a-fA-F]{4})
                root ::= "\\"" char{1,4} "\\"" space
                space ::= | " " | "\\n" [ \\t]{0,20}
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testBoolean() {
        var schema = """
                {
                    "type": "boolean"
                }
                """;
        var expectedGrammar = """
                root ::= ("true" | "false") space
                space ::= | " " | "\\n" [ \\t]{0,20}
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testInteger() {
        var schema = """
                {
                    "type": "integer"
                }
                """;
        var expectedGrammar = """
                integral-part ::= [0] | [1-9] [0-9]{0,15}
                root ::= ("-"? integral-part) space
                space ::= | " " | "\\n" [ \\t]{0,20}
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testStringConst() {
        var schema = """
                {
                    "const": "foo"
                }
                """;
        var expectedGrammar = """
                root ::= "\\"foo\\"" space
                space ::= | " " | "\\n" [ \\t]{0,20}
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testNonStringConst() {
        var schema = """
                {
                    "const": 123
                }
                """;
        var expectedGrammar = """
                root ::= "123" space
                space ::= | " " | "\\n" [ \\t]{0,20}
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testNonStringEnum() {
        var schema = """
                {
                    "enum": ["red", "amber", "green", null, 42, ["foo"]]
                }
                """;
        var expectedGrammar = """
                root ::= ("\\"red\\"" | "\\"amber\\"" | "\\"green\\"" | "null" | "42" | "[\\"foo\\"]") space
                space ::= | " " | "\\n" [ \\t]{0,20}
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testStringArray() {
        var schema = """
                {
                    "type": "array",
                    "prefixItems": { "type": "string" }
                }
                """;
        var expectedGrammar = """
                char ::= [^"\\\\\\x7F\\x00-\\x1F] | [\\\\] (["\\\\bfnrt] | "u" [0-9a-fA-F]{4})
                root ::= "[" space (string ("," space string)*)? "]" space
                space ::= | " " | "\\n" [ \\t]{0,20}
                string ::= "\\"" char* "\\"" space
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testNullableStringArray() {
        var schema = """
                {
                    "type": ["array", "null"],
                    "prefixItems": { "type": "string" }
                }
                """;
        var expectedGrammar = """
                alternative-0 ::= "[" space (string ("," space string)*)? "]" space
                char ::= [^"\\\\\\x7F\\x00-\\x1F] | [\\\\] (["\\\\bfnrt] | "u" [0-9a-fA-F]{4})
                null ::= "null" space
                root ::= alternative-0 | null
                space ::= | " " | "\\n" [ \\t]{0,20}
                string ::= "\\"" char* "\\"" space
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testTuple1() {
        var schema = """
                {
                    "prefixItems": [{ "type": "string" }]
                }
                """;
        var expectedGrammar = """
                char ::= [^"\\\\\\x7F\\x00-\\x1F] | [\\\\] (["\\\\bfnrt] | "u" [0-9a-fA-F]{4})
                root ::= "[" space string "]" space
                space ::= | " " | "\\n" [ \\t]{0,20}
                string ::= "\\"" char* "\\"" space
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testTuple2() {
        var schema = """
                {
                    "prefixItems": [{ "type": "string" }, { "type": "number" }]
                }
                """;
        var expectedGrammar = """
                char ::= [^"\\\\\\x7F\\x00-\\x1F] | [\\\\] (["\\\\bfnrt] | "u" [0-9a-fA-F]{4})
                decimal-part ::= [0-9]{1,16}
                integral-part ::= [0] | [1-9] [0-9]{0,15}
                number ::= ("-"? integral-part) ("." decimal-part)? ([eE] [-+]? integral-part)? space
                root ::= "[" space string "," space number "]" space
                space ::= | " " | "\\n" [ \\t]{0,20}
                string ::= "\\"" char* "\\"" space
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testNumber() {
        var schema = """
                {
                    "type": "number"
                }
                """;
        var expectedGrammar = """
                decimal-part ::= [0-9]{1,16}
                integral-part ::= [0] | [1-9] [0-9]{0,15}
                root ::= ("-"? integral-part) ("." decimal-part)? ([eE] [-+]? integral-part)? space
                space ::= | " " | "\\n" [ \\t]{0,20}
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testMinItems() {
        var schema = """
                {
                    "items": {
                        "type": "boolean"
                    },
                    "minItems": 2
                }
                """;
        var expectedGrammar = """
                boolean ::= ("true" | "false") space
                root ::= "[" space boolean ("," space boolean)+ "]" space
                space ::= | " " | "\\n" [ \\t]{0,20}
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testMaxItems1() {
        var schema = """
                {
                    "items": {
                        "type": "boolean"
                    },
                    "maxItems": 1
                }
                """;
        var expectedGrammar = """
                boolean ::= ("true" | "false") space
                root ::= "[" space boolean? "]" space
                space ::= | " " | "\\n" [ \\t]{0,20}
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testMaxItems2() {
        var schema = """
                {
                    "items": {
                        "type": "boolean"
                    },
                    "maxItems": 2
                }
                """;
        var expectedGrammar = """
                boolean ::= ("true" | "false") space
                root ::= "[" space (boolean ("," space boolean)?)? "]" space
                space ::= | " " | "\\n" [ \\t]{0,20}
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testMinAndMaxItems() {
        var schema = """
                {
                    "items": {
                        "type": ["number", "integer"]
                    },
                    "minItems": 3,
                    "maxItems": 5
                }
                """;
        var expectedGrammar = """
                decimal-part ::= [0-9]{1,16}
                integer ::= ("-"? integral-part) space
                integral-part ::= [0] | [1-9] [0-9]{0,15}
                item ::= number | integer
                number ::= ("-"? integral-part) ("." decimal-part)? ([eE] [-+]? integral-part)? space
                root ::= "[" space item ("," space item){2,4} "]" space
                space ::= | " " | "\\n" [ \\t]{0,20}
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testMinAndMaxItemsWithMinAndMaxValuesAcrossZero() {
        var schema = """
                {
                    "items": {
                        "type": "integer",
                        "minimum": -12,
                        "maximum": 207
                    },
                    "minItems": 3,
                    "maxItems": 5
                }
                """;
        var expectedGrammar = """
                item ::= ("-" ([0-9] | [1] [0-2]) | [0-9] | ([1-8] [0-9] | [9] [0-9]) | ([1] [0-9]{2} | [2] [0] [0-7])) space
                root ::= "[" space item ("," space item){2,4} "]" space
                space ::= | " " | "\\n" [ \\t]{0,20}
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testMinAndMaxItemsWithMinAndMaxValues() {
        var schema = """
                {
                    "items": {
                        "type": "integer",
                        "minimum": 12,
                        "maximum": 207
                    },
                    "minItems": 3,
                    "maxItems": 5
                }
                """;
        var expectedGrammar = """
                item ::= (([1] ([2-9]) | [2-9] [0-9]) | ([1] [0-9]{2} | [2] [0] [0-7])) space
                root ::= "[" space item ("," space item){2,4} "]" space
                space ::= | " " | "\\n" [ \\t]{0,20}
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testObjectWithRequiredProperties() {
        var schema = """
                {
                    "type": "object",
                    "properties": {
                        "b": {"type": "string"},
                        "c": {"type": "string"},
                        "a": {"type": "string"}
                    },
                    "required": ["a", "b", "c"],
                    "additionalProperties": false
                }
                """;
        var expectedGrammar = """
                a-kv ::= "\\"a\\"" space ":" space string
                b-kv ::= "\\"b\\"" space ":" space string
                c-kv ::= "\\"c\\"" space ":" space string
                char ::= [^"\\\\\\x7F\\x00-\\x1F] | [\\\\] (["\\\\bfnrt] | "u" [0-9a-fA-F]{4})
                root ::= "{" space b-kv "," space c-kv "," space a-kv "}" space
                space ::= | " " | "\\n" [ \\t]{0,20}
                string ::= "\\"" char* "\\"" space
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testObjectWithOptionalProperties() {
        var schema = """
                {
                    "properties": {
                        "a": {"type": "string"}
                    },
                    "additionalProperties": false
                }
                """;
        var expectedGrammar = """
                a-kv ::= "\\"a\\"" space ":" space string
                char ::= [^"\\\\\\x7F\\x00-\\x1F] | [\\\\] (["\\\\bfnrt] | "u" [0-9a-fA-F]{4})
                root ::= "{" space  (a-kv )? "}" space
                space ::= | " " | "\\n" [ \\t]{0,20}
                string ::= "\\"" char* "\\"" space
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testObjectWithMultipleOptionalProperties() {
        var schema = """
                {
                    "properties": {
                        "a": {"type": "string"},
                        "b": {"type": "string"},
                        "c": {"type": "string"}
                    },
                    "additionalProperties": false
                }
                """;
        var expectedGrammar = """
                a-kv ::= "\\"a\\"" space ":" space string
                a-rest ::= ( "," space b-kv )? b-rest
                b-kv ::= "\\"b\\"" space ":" space string
                b-rest ::= ( "," space c-kv )?
                c-kv ::= "\\"c\\"" space ":" space string
                char ::= [^"\\\\\\x7F\\x00-\\x1F] | [\\\\] (["\\\\bfnrt] | "u" [0-9a-fA-F]{4})
                root ::= "{" space  (a-kv a-rest | b-kv b-rest | c-kv )? "}" space
                space ::= | " " | "\\n" [ \\t]{0,20}
                string ::= "\\"" char* "\\"" space
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testObjectWithRequiredAndOptionalProperties() {
        var schema = """
                {
                    "properties": {
                        "b": {"type": "string"},
                        "a": {"type": "string"},
                        "d": {"type": "string"},
                        "c": {"type": "string"}
                    },
                    "required": ["a", "b"],
                    "additionalProperties": false
                }
                """;
        var expectedGrammar = """
                a-kv ::= "\\"a\\"" space ":" space string
                b-kv ::= "\\"b\\"" space ":" space string
                c-kv ::= "\\"c\\"" space ":" space string
                char ::= [^"\\\\\\x7F\\x00-\\x1F] | [\\\\] (["\\\\bfnrt] | "u" [0-9a-fA-F]{4})
                d-kv ::= "\\"d\\"" space ":" space string
                d-rest ::= ( "," space c-kv )?
                root ::= "{" space b-kv "," space a-kv ( "," space ( d-kv d-rest | c-kv ) )? "}" space
                space ::= | " " | "\\n" [ \\t]{0,20}
                string ::= "\\"" char* "\\"" space
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testObjectWithAdditionalProperties() {
        var schema = """
                {
                    "type": "object",
                    "additionalProperties": {"type": "array", "items": {"type": "number"}}
                }
                """;
        var expectedGrammar = """
                additional-kv ::= string ":" space additional-value
                additional-value ::= "[" space (number ("," space number)*)? "]" space
                char ::= [^"\\\\\\x7F\\x00-\\x1F] | [\\\\] (["\\\\bfnrt] | "u" [0-9a-fA-F]{4})
                decimal-part ::= [0-9]{1,16}
                integral-part ::= [0] | [1-9] [0-9]{0,15}
                number ::= ("-"? integral-part) ("." decimal-part)? ([eE] [-+]? integral-part)? space
                root ::= "{" space  (additional-kv ( "," space additional-kv )* )? "}" space
                space ::= | " " | "\\n" [ \\t]{0,20}
                string ::= "\\"" char* "\\"" space
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testObjectWithAdditionalPropertiesTrue() {
        var schema = """
                {
                    "type": "object",
                    "additionalProperties": true
                }
                """;
        var expectedGrammar = """
                array ::= "[" space ( value ("," space value)* )? "]" space
                boolean ::= ("true" | "false") space
                char ::= [^"\\\\\\x7F\\x00-\\x1F] | [\\\\] (["\\\\bfnrt] | "u" [0-9a-fA-F]{4})
                decimal-part ::= [0-9]{1,16}
                integral-part ::= [0] | [1-9] [0-9]{0,15}
                null ::= "null" space
                number ::= ("-"? integral-part) ("." decimal-part)? ([eE] [-+]? integral-part)? space
                object ::= "{" space ( string ":" space value ("," space string ":" space value)* )? "}" space
                root ::= object
                space ::= | " " | "\\n" [ \\t]{0,20}
                string ::= "\\"" char* "\\"" space
                value ::= object | array | string | number | boolean | null
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testObjectWithImplicitAdditionalProperties() {
        var schema = """
                {
                    "type": "object"
                }
                """;
        var expectedGrammar = """
                array ::= "[" space ( value ("," space value)* )? "]" space
                boolean ::= ("true" | "false") space
                char ::= [^"\\\\\\x7F\\x00-\\x1F] | [\\\\] (["\\\\bfnrt] | "u" [0-9a-fA-F]{4})
                decimal-part ::= [0-9]{1,16}
                integral-part ::= [0] | [1-9] [0-9]{0,15}
                null ::= "null" space
                number ::= ("-"? integral-part) ("." decimal-part)? ([eE] [-+]? integral-part)? space
                object ::= "{" space ( string ":" space value ("," space string ":" space value)* )? "}" space
                root ::= object
                space ::= | " " | "\\n" [ \\t]{0,20}
                string ::= "\\"" char* "\\"" space
                value ::= object | array | string | number | boolean | null
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testEmptyObjectWithoutAdditionalProperties() {
        var schema = """
                {
                    "type": "object",
                    "additionalProperties": false
                }
                """;
        var expectedGrammar = """
                root ::= "{" space  "}" space
                space ::= | " " | "\\n" [ \\t]{0,20}
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testObjectWithRequiredAndAdditionalProperties() {
        var schema = """
                {
                    "type": "object",
                    "properties": {
                        "a": {"type": "number"}
                    },
                    "required": ["a"],
                    "additionalProperties": {"type": "string"}
                }
                """;
        var expectedGrammar = """
                a-kv ::= "\\"a\\"" space ":" space number
                additional-k ::= ["] ( [a] char+ | [^"a] char* )? ["] space
                additional-kv ::= additional-k ":" space string
                char ::= [^"\\\\\\x7F\\x00-\\x1F] | [\\\\] (["\\\\bfnrt] | "u" [0-9a-fA-F]{4})
                decimal-part ::= [0-9]{1,16}
                integral-part ::= [0] | [1-9] [0-9]{0,15}
                number ::= ("-"? integral-part) ("." decimal-part)? ([eE] [-+]? integral-part)? space
                root ::= "{" space a-kv ( "," space ( additional-kv ( "," space additional-kv )* ) )? "}" space
                space ::= | " " | "\\n" [ \\t]{0,20}
                string ::= "\\"" char* "\\"" space
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testObjectWithOptionalAndAdditionalProperties() {
        var schema = """
                {
                    "type": "object",
                    "properties": {
                        "a": {"type": "number"}
                    },
                    "additionalProperties": {"type": "number"}
                }
                """;
        var expectedGrammar = """
                a-kv ::= "\\"a\\"" space ":" space number
                a-rest ::= ( "," space additional-kv )*
                additional-k ::= ["] ( [a] char+ | [^"a] char* )? ["] space
                additional-kv ::= additional-k ":" space number
                char ::= [^"\\\\\\x7F\\x00-\\x1F] | [\\\\] (["\\\\bfnrt] | "u" [0-9a-fA-F]{4})
                decimal-part ::= [0-9]{1,16}
                integral-part ::= [0] | [1-9] [0-9]{0,15}
                number ::= ("-"? integral-part) ("." decimal-part)? ([eE] [-+]? integral-part)? space
                root ::= "{" space  (a-kv a-rest | additional-kv ( "," space additional-kv )* )? "}" space
                space ::= | " " | "\\n" [ \\t]{0,20}
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testObjectWithRequiredOptionalAndAdditionalProperties() {
        var schema = """
                {
                    "type": "object",
                    "properties": {
                        "and": {"type": "number"},
                        "also": {"type": "number"}
                    },
                    "required": ["and"],
                    "additionalProperties": {"type": "number"}
                }
                """;
        var expectedGrammar = """
                additional-k ::= ["] ( [a] ([l] ([s] ([o] char+ | [^"o] char*) | [^"s] char*) | [n] ([d] char+ | [^"d] char*) | [^"ln] char*) | [^"a] char* )? ["] space
                additional-kv ::= additional-k ":" space number
                also-kv ::= "\\"also\\"" space ":" space number
                also-rest ::= ( "," space additional-kv )*
                and-kv ::= "\\"and\\"" space ":" space number
                char ::= [^"\\\\\\x7F\\x00-\\x1F] | [\\\\] (["\\\\bfnrt] | "u" [0-9a-fA-F]{4})
                decimal-part ::= [0-9]{1,16}
                integral-part ::= [0] | [1-9] [0-9]{0,15}
                number ::= ("-"? integral-part) ("." decimal-part)? ([eE] [-+]? integral-part)? space
                root ::= "{" space and-kv ( "," space ( also-kv also-rest | additional-kv ( "," space additional-kv )* ) )? "}" space
                space ::= | " " | "\\n" [ \\t]{0,20}
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testOptionalPropsWithEmptyName() {
        var schema = """
                {
                    "properties": {
                        "": {"type": "integer"},
                        "a": {"type": "integer"}
                    },
                    "additionalProperties": {"type": "integer"}
                }
                """;
        var expectedGrammar = """
                -kv ::= "\\"\\"" space ":" space integer
                -rest ::= ( "," space a-kv )? a-rest
                a-kv ::= "\\"a\\"" space ":" space integer
                a-rest ::= ( "," space additional-kv )*
                additional-k ::= ["] ( [a] char+ | [^"a] char* ) ["] space
                additional-kv ::= additional-k ":" space integer
                char ::= [^"\\\\\\x7F\\x00-\\x1F] | [\\\\] (["\\\\bfnrt] | "u" [0-9a-fA-F]{4})
                integer ::= ("-"? integral-part) space
                integral-part ::= [0] | [1-9] [0-9]{0,15}
                root ::= "{" space  (-kv -rest | a-kv a-rest | additional-kv ( "," space additional-kv )* )? "}" space
                space ::= | " " | "\\n" [ \\t]{0,20}
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testOptionalPropsWithNestedNames() {
        var schema = """
                {
                    "properties": {
                        "a": {"type": "integer"},
                        "aa": {"type": "integer"}
                    },
                    "additionalProperties": {"type": "integer"}
                }
                """;
        var expectedGrammar = """
                a-kv ::= "\\"a\\"" space ":" space integer
                a-rest ::= ( "," space aa-kv )? aa-rest
                aa-kv ::= "\\"aa\\"" space ":" space integer
                aa-rest ::= ( "," space additional-kv )*
                additional-k ::= ["] ( [a] ([a] char+ | [^"a] char*) | [^"a] char* )? ["] space
                additional-kv ::= additional-k ":" space integer
                char ::= [^"\\\\\\x7F\\x00-\\x1F] | [\\\\] (["\\\\bfnrt] | "u" [0-9a-fA-F]{4})
                integer ::= ("-"? integral-part) space
                integral-part ::= [0] | [1-9] [0-9]{0,15}
                root ::= "{" space  (a-kv a-rest | aa-kv aa-rest | additional-kv ( "," space additional-kv )* )? "}" space
                space ::= | " " | "\\n" [ \\t]{0,20}
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testOptionalPropsWithCommonPrefix() {
        var schema = """
                {
                    "properties": {
                        "ab": {"type": "integer"},
                        "ac": {"type": "integer"}
                    },
                    "additionalProperties": {"type": "integer"}
                }
                """;
        var expectedGrammar = """
                ab-kv ::= "\\"ab\\"" space ":" space integer
                ab-rest ::= ( "," space ac-kv )? ac-rest
                ac-kv ::= "\\"ac\\"" space ":" space integer
                ac-rest ::= ( "," space additional-kv )*
                additional-k ::= ["] ( [a] ([b] char+ | [c] char+ | [^"bc] char*) | [^"a] char* )? ["] space
                additional-kv ::= additional-k ":" space integer
                char ::= [^"\\\\\\x7F\\x00-\\x1F] | [\\\\] (["\\\\bfnrt] | "u" [0-9a-fA-F]{4})
                integer ::= ("-"? integral-part) space
                integral-part ::= [0] | [1-9] [0-9]{0,15}
                root ::= "{" space  (ab-kv ab-rest | ac-kv ac-rest | additional-kv ( "," space additional-kv )* )? "}" space
                space ::= | " " | "\\n" [ \\t]{0,20}
                """;


        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }

    @Test
    public void testConflictingNames() {
        var schema = """
                {
                    "type": "object",
                    "properties": {
                        "number": {
                        "type": "object",
                        "properties": {
                            "number": {
                            "type": "object",
                                "properties": {
                                    "root": {
                                        "type": "number"
                                    }
                                },
                                "required": [
                                    "root"
                                ],
                                "additionalProperties": false
                            }
                        },
                        "required": [
                            "number"
                        ],
                        "additionalProperties": false
                        }
                    },
                    "required": [
                        "number"
                    ],
                    "additionalProperties": false,
                    "definitions": {}
                }
                """;
        var expectedGrammar = """
                decimal-part ::= [0-9]{1,16}
                integral-part ::= [0] | [1-9] [0-9]{0,15}
                number ::= ("-"? integral-part) ("." decimal-part)? ([eE] [-+]? integral-part)? space
                number- ::= "{" space number-number-kv "}" space
                number-kv ::= "\\"number\\"" space ":" space number-
                number-number ::= "{" space number-number-root-kv "}" space
                number-number-kv ::= "\\"number\\"" space ":" space number-number
                number-number-root-kv ::= "\\"root\\"" space ":" space number
                root ::= "{" space number-kv "}" space
                space ::= | " " | "\\n" [ \\t]{0,20}
                """;

        var actualGrammar = JsonSchemaToGrammar.convert(schema);
        assertEquals(expectedGrammar, actualGrammar);
    }
}
