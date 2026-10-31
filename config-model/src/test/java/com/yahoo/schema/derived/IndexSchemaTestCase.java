// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.StructDataType;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.vespa.config.search.IndexschemaConfig;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Simon Thoresen Hult
 */
public class IndexSchemaTestCase {

    @Test
    void requireThatPrimitiveIsNotFlattened() {
        assertFlat(new Field("foo", DataType.BYTE), new Field("foo", DataType.BYTE));
        assertFlat(new Field("foo", DataType.DOUBLE), new Field("foo", DataType.DOUBLE));
        assertFlat(new Field("foo", DataType.FLOAT), new Field("foo", DataType.FLOAT));
        assertFlat(new Field("foo", DataType.INT), new Field("foo", DataType.INT));
        assertFlat(new Field("foo", DataType.LONG), new Field("foo", DataType.LONG));
        assertFlat(new Field("foo", DataType.RAW), new Field("foo", DataType.RAW));
        assertFlat(new Field("foo", DataType.STRING), new Field("foo", DataType.STRING));
        assertFlat(new Field("foo", DataType.URI), new Field("foo", DataType.URI));
        assertFlat(new Field("foo", DataType.PREDICATE), new Field("foo", DataType.PREDICATE));
    }

    @Test
    void requireThatArrayOfPrimitiveIsNotFlattened() {
        assertFlat(new Field("foo", DataType.getArray(DataType.BYTE)),
                new Field("foo", DataType.getArray(DataType.BYTE)));
        assertFlat(new Field("foo", DataType.getArray(DataType.DOUBLE)),
                new Field("foo", DataType.getArray(DataType.DOUBLE)));
        assertFlat(new Field("foo", DataType.getArray(DataType.FLOAT)),
                new Field("foo", DataType.getArray(DataType.FLOAT)));
        assertFlat(new Field("foo", DataType.getArray(DataType.INT)),
                new Field("foo", DataType.getArray(DataType.INT)));
        assertFlat(new Field("foo", DataType.getArray(DataType.LONG)),
                new Field("foo", DataType.getArray(DataType.LONG)));
        assertFlat(new Field("foo", DataType.getArray(DataType.RAW)),
                new Field("foo", DataType.getArray(DataType.RAW)));
        assertFlat(new Field("foo", DataType.getArray(DataType.STRING)),
                new Field("foo", DataType.getArray(DataType.STRING)));
        assertFlat(new Field("foo", DataType.getArray(DataType.URI)),
                new Field("foo", DataType.getArray(DataType.URI)));
        assertFlat(new Field("foo", DataType.getArray(DataType.PREDICATE)),
                new Field("foo", DataType.getArray(DataType.PREDICATE)));
    }

    @Test
    void requireThatStructIsFlattened() {
        StructDataType type = new StructDataType("my_struct");
        type.addField(new Field("my_byte", DataType.BYTE));
        type.addField(new Field("my_double", DataType.DOUBLE));
        type.addField(new Field("my_float", DataType.FLOAT));
        type.addField(new Field("my_int", DataType.INT));
        type.addField(new Field("my_long", DataType.LONG));
        type.addField(new Field("my_raw", DataType.RAW));
        type.addField(new Field("my_string", DataType.STRING));
        type.addField(new Field("my_uri", DataType.URI));

        assertFlat(new Field("foo", type),
                new Field("foo.my_byte", DataType.BYTE),
                new Field("foo.my_double", DataType.DOUBLE),
                new Field("foo.my_float", DataType.FLOAT),
                new Field("foo.my_int", DataType.INT),
                new Field("foo.my_long", DataType.LONG),
                new Field("foo.my_raw", DataType.RAW),
                new Field("foo.my_string", DataType.STRING),
                new Field("foo.my_uri", DataType.URI));
    }

    @Test
    void requireThatArrayOfStructIsFlattened() {
        StructDataType type = new StructDataType("my_struct");
        type.addField(new Field("my_byte", DataType.BYTE));
        type.addField(new Field("my_double", DataType.DOUBLE));
        type.addField(new Field("my_float", DataType.FLOAT));
        type.addField(new Field("my_int", DataType.INT));
        type.addField(new Field("my_long", DataType.LONG));
        type.addField(new Field("my_raw", DataType.RAW));
        type.addField(new Field("my_string", DataType.STRING));
        type.addField(new Field("my_uri", DataType.URI));

        assertFlat(new Field("foo", DataType.getArray(type)),
                new Field("foo.my_byte", DataType.getArray(DataType.BYTE)),
                new Field("foo.my_double", DataType.getArray(DataType.DOUBLE)),
                new Field("foo.my_float", DataType.getArray(DataType.FLOAT)),
                new Field("foo.my_int", DataType.getArray(DataType.INT)),
                new Field("foo.my_long", DataType.getArray(DataType.LONG)),
                new Field("foo.my_raw", DataType.getArray(DataType.RAW)),
                new Field("foo.my_string", DataType.getArray(DataType.STRING)),
                new Field("foo.my_uri", DataType.getArray(DataType.URI)));
    }

    @Test
    void requireThatArrayOfArrayOfStructIsFlattened() {
        StructDataType type = new StructDataType("my_struct");
        type.addField(new Field("my_byte", DataType.BYTE));
        type.addField(new Field("my_double", DataType.DOUBLE));
        type.addField(new Field("my_float", DataType.FLOAT));
        type.addField(new Field("my_int", DataType.INT));
        type.addField(new Field("my_long", DataType.LONG));
        type.addField(new Field("my_raw", DataType.RAW));
        type.addField(new Field("my_string", DataType.STRING));
        type.addField(new Field("my_uri", DataType.URI));

        assertFlat(new Field("foo", DataType.getArray(DataType.getArray(type))),
                new Field("foo.my_byte", DataType.getArray(DataType.getArray(DataType.BYTE))),
                new Field("foo.my_double", DataType.getArray(DataType.getArray(DataType.DOUBLE))),
                new Field("foo.my_float", DataType.getArray(DataType.getArray(DataType.FLOAT))),
                new Field("foo.my_int", DataType.getArray(DataType.getArray(DataType.INT))),
                new Field("foo.my_long", DataType.getArray(DataType.getArray(DataType.LONG))),
                new Field("foo.my_raw", DataType.getArray(DataType.getArray(DataType.RAW))),
                new Field("foo.my_string", DataType.getArray(DataType.getArray(DataType.STRING))),
                new Field("foo.my_uri", DataType.getArray(DataType.getArray(DataType.URI))));
    }

    @Test
    void requireThatStructWithArrayFieldIsFlattened() {
        StructDataType type = new StructDataType("my_struct");
        type.addField(new Field("my_byte", DataType.getArray(DataType.BYTE)));
        type.addField(new Field("my_double", DataType.getArray(DataType.DOUBLE)));
        type.addField(new Field("my_float", DataType.getArray(DataType.FLOAT)));
        type.addField(new Field("my_int", DataType.getArray(DataType.INT)));
        type.addField(new Field("my_long", DataType.getArray(DataType.LONG)));
        type.addField(new Field("my_raw", DataType.getArray(DataType.RAW)));
        type.addField(new Field("my_string", DataType.getArray(DataType.STRING)));
        type.addField(new Field("my_uri", DataType.getArray(DataType.URI)));

        assertFlat(new Field("foo", type),
                new Field("foo.my_byte", DataType.getArray(DataType.BYTE)),
                new Field("foo.my_double", DataType.getArray(DataType.DOUBLE)),
                new Field("foo.my_float", DataType.getArray(DataType.FLOAT)),
                new Field("foo.my_int", DataType.getArray(DataType.INT)),
                new Field("foo.my_long", DataType.getArray(DataType.LONG)),
                new Field("foo.my_raw", DataType.getArray(DataType.RAW)),
                new Field("foo.my_string", DataType.getArray(DataType.STRING)),
                new Field("foo.my_uri", DataType.getArray(DataType.URI)));
    }

    @Test
    void requireThatStructWithArrayOfArrayFieldIsFlattened() {
        StructDataType type = new StructDataType("my_struct");
        type.addField(new Field("my_byte", DataType.getArray(DataType.getArray(DataType.BYTE))));
        type.addField(new Field("my_double", DataType.getArray(DataType.getArray(DataType.DOUBLE))));
        type.addField(new Field("my_float", DataType.getArray(DataType.getArray(DataType.FLOAT))));
        type.addField(new Field("my_int", DataType.getArray(DataType.getArray(DataType.INT))));
        type.addField(new Field("my_long", DataType.getArray(DataType.getArray(DataType.LONG))));
        type.addField(new Field("my_raw", DataType.getArray(DataType.getArray(DataType.RAW))));
        type.addField(new Field("my_string", DataType.getArray(DataType.getArray(DataType.STRING))));
        type.addField(new Field("my_uri", DataType.getArray(DataType.getArray(DataType.URI))));

        assertFlat(new Field("foo", type),
                new Field("foo.my_byte", DataType.getArray(DataType.getArray(DataType.BYTE))),
                new Field("foo.my_double", DataType.getArray(DataType.getArray(DataType.DOUBLE))),
                new Field("foo.my_float", DataType.getArray(DataType.getArray(DataType.FLOAT))),
                new Field("foo.my_int", DataType.getArray(DataType.getArray(DataType.INT))),
                new Field("foo.my_long", DataType.getArray(DataType.getArray(DataType.LONG))),
                new Field("foo.my_raw", DataType.getArray(DataType.getArray(DataType.RAW))),
                new Field("foo.my_string", DataType.getArray(DataType.getArray(DataType.STRING))),
                new Field("foo.my_uri", DataType.getArray(DataType.getArray(DataType.URI))));
    }

    @Test
    void requireThatArrayOfStructWithArrayFieldIsFlattened() {
        StructDataType type = new StructDataType("my_struct");
        type.addField(new Field("my_byte", DataType.getArray(DataType.BYTE)));
        type.addField(new Field("my_double", DataType.getArray(DataType.DOUBLE)));
        type.addField(new Field("my_float", DataType.getArray(DataType.FLOAT)));
        type.addField(new Field("my_int", DataType.getArray(DataType.INT)));
        type.addField(new Field("my_long", DataType.getArray(DataType.LONG)));
        type.addField(new Field("my_raw", DataType.getArray(DataType.RAW)));
        type.addField(new Field("my_string", DataType.getArray(DataType.STRING)));
        type.addField(new Field("my_uri", DataType.getArray(DataType.URI)));

        assertFlat(new Field("foo", DataType.getArray(type)),
                new Field("foo.my_byte", DataType.getArray(DataType.getArray(DataType.BYTE))),
                new Field("foo.my_double", DataType.getArray(DataType.getArray(DataType.DOUBLE))),
                new Field("foo.my_float", DataType.getArray(DataType.getArray(DataType.FLOAT))),
                new Field("foo.my_int", DataType.getArray(DataType.getArray(DataType.INT))),
                new Field("foo.my_long", DataType.getArray(DataType.getArray(DataType.LONG))),
                new Field("foo.my_raw", DataType.getArray(DataType.getArray(DataType.RAW))),
                new Field("foo.my_string", DataType.getArray(DataType.getArray(DataType.STRING))),
                new Field("foo.my_uri", DataType.getArray(DataType.getArray(DataType.URI))));
    }

    @Test
    void requireThatUriFieldsAreListed() throws ParseException {
        var schema = ApplicationBuilder.createFromString(
                joinLines("search test {",
                        "  document test {",
                        "    field my_uri type uri {",
                        "      indexing: index",
                        "    }",
                        "    field my_uri_array type array<uri> {",
                        "      indexing: index",
                        "    }",
                        "    field my_uri_summary type uri {",
                        "      indexing: summary",
                        "    }",
                        "    field my_string type string {",
                        "      indexing: index",
                        "    }",
                        "  }",
                        "}")).getSchema();
        var builder = new IndexschemaConfig.Builder();
        new IndexSchema(schema).getConfig(builder);
        var config = builder.build();
        assertTrue(config.explicituris());
        assertEquals(List.of("my_uri", "my_uri_array"),
                     config.urifield().stream().map(IndexschemaConfig.Urifield::name).toList());
    }

    private static void assertFlat(Field fieldToFlatten, Field... expectedFields) {
        List<Field> actual = new LinkedList<>(IndexSchema.flattenField(fieldToFlatten));
        List<Field> expected = new LinkedList<>(List.of(expectedFields));
        Collections.sort(actual);
        Collections.sort(expected);
        for (Field field : actual) {
            if (!expected.remove(field)) {
                fail("Unexpected field: " + field);
            }
        }
        assertTrue(expected.isEmpty(), "Missing fields: " + expected);
    }

}
