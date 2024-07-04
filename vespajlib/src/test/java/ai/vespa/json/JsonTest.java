package ai.vespa.json;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bjorncs
 */
class JsonTest {

    @Test
    void parses_json_correctly() {
        var text =
                """
                {
                    "string": "bar",
                    "integer": 42,
                    "floaty": 8.25,
                    "bool": true,
                    "array": [1, 2, 3],
                    "quux": {
                        "corge": "grault"
                    },
                    "timestamp": "2021-06-01T12:00:00Z"
                }
                """;
        var json = Json.of(text);

        // Primitive members
        assertEquals("bar", json.f("string").asString());
        assertTrue(json.f("string").asOptionalString().isPresent());
        assertEquals("bar", json.f("string").asOptionalString().get());
        assertEquals(42L, json.f("integer").asLong());
        assertEquals(42D, json.f("integer").asDouble());
        assertEquals(8.25D, json.f("floaty").asDouble());
        assertEquals(8L, json.f("floaty").asLong());
        assertTrue(json.f("bool").asBool());
        assertEquals("2021-06-01T12:00:00Z", json.f("timestamp").asInstant().toString());

        // Array member
        assertEquals(3, json.f("array").length());
        assertEquals(1L, json.f("array").entry(0).asLong());
        assertEquals(2L, json.f("array").entry(1).asLong());
        assertEquals(3L, json.f("array").entry(2).asLong());
        json.f("array").forEachEntry((i, entry) -> assertEquals(i + 1, entry.asLong()));
        int counter = 0;
        for (var entry : json.f("array")) {
            assertEquals(++counter, entry.asLong());
        }

        // Object member
        assertEquals("grault", json.f("quux").f("corge").asString());
        json.f("quux").forEachField((name, child) -> {
            assertEquals("corge", name);
            assertEquals("grault", child.asString());
        });
    }

    @Test
    void throws_on_missing_and_invalid_members() {
        var text =
                """
                {
                    "string": "bar"
                }
                """;
        var json = Json.of(text);

        var exception = assertThrows(InvalidJsonException.class, () -> json.f("unknown").asString());
        assertEquals("Missing JSON member 'unknown'", exception.getMessage());

        exception = assertThrows(InvalidJsonException.class, () -> json.a(0));
        assertEquals("Expected JSON to be a 'array' but got 'object'", exception.getMessage());

        exception = assertThrows(InvalidJsonException.class, () -> json.f("string").f("unknown"));
        assertEquals("Expected JSON member 'string' to be a 'object' but got 'string'", exception.getMessage());

        exception = assertThrows(InvalidJsonException.class, () -> json.f("string").asLong());
        assertEquals("Expected JSON member 'string' to be a 'integer' or 'float' but got 'string'", exception.getMessage());

        exception = assertThrows(InvalidJsonException.class, () -> json.f("string").asInstant());
        assertEquals("Expected JSON member 'string' to be a valid timestamp: Text 'bar' could not be parsed at index 0", exception.getMessage());
    }

    @Test
    void fallback_to_default_if_field_missing() {
        var text =
                """
                {
                    "string": "bar"
                }
                """;
        var json = Json.of(text);
        assertEquals("foo", json.f("unknown").asString("foo"));
        assertEquals("foo", json.f("unknown").asOptionalString().orElse("foo"));
        assertEquals("bar", json.f("string").asString("foo"));
        assertEquals("bar", json.f("string").asOptionalString().orElse("foo"));
    }

    @Test
    void builds_expected_json() {
        var expected =
                """
                {
                  "string": "bar",
                  "integer": 42,
                  "floaty": 8.25,
                  "bool": true,
                  "array": [
                    1,
                    2,
                    3
                  ],
                  "quux": {
                    "corge": "grault"
                  }
                }
                """;
        var json = Json.Builder.newObject()
                .set("string", "bar")
                .set("integer", 42)
                .set("floaty", 8.25)
                .set("bool", true)
                .set("array", Json.Builder.newArray().add(1).add(2).add(3))
                .set("quux", Json.Builder.newObject().set("corge", "grault"))
                .build()
                .toJson(true);
        assertEquals(expected, json);
    }

    @Test
    void add_all() {
        var expected =
                """
                [
                  1,
                  2,
                  3,
                  4,
                  5,
                  6
                ]
                """;
        var json = Json.Builder.newArray()
                .addAll(Json.Builder.Array.newArray().add(1).add(2).add(3))
                .add(4)
                .addAll(Json.Builder.Array.newArray().add(5))
                .add(6)
                .build()
                .toJson(true);
        assertEquals(expected, json);
    }

    @Test
    void collectors() {
        var expected = Json.Builder.Array.newArray()
                .add(1).add(2).add(3).add(4).add(5).add(6)
                .build()
                .toJson(false);
        var actual = Stream.of(1, 2, 3, 4, 5, 6)
                .collect(Json.Collectors.toArray(Json.Builder.Array::add))
                .toJson(false);
        assertEquals(expected, actual);
    }

    @Test
    void create_builder_from_existing_json() {
        var jsonArray = Json.Builder.newArray()
                .add(1)
                .add(2)
                .add(3)
                .build();
        var jsonArrayCopy = Json.Builder.fromArray(jsonArray).build();
        assertEquals(jsonArray.toJson(false), jsonArrayCopy.toJson(false));

        var jsonObject = Json.Builder.newObject()
                .set("foo", "bar")
                .set("baz", Json.Builder.newArray().add("qux"))
                .build();
        var jsonObjectCopy = Json.Builder.fromObject(jsonObject).build();
        assertEquals(jsonObject.toJson(false), jsonObjectCopy.toJson(false));
    }
}
