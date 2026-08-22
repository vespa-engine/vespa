// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.document.DataType;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.Attribute;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the synthetic key-value attribute created for map fields with 'map: fast-search'.
 *
 * @author johsol
 */
public class CreateFastMapSearchTest {

    @Test
    void requireKeyValueFieldIsCreatedForFastSearchMap() throws ParseException {
        var schema = build(fastSearchMap("map<string, string>"));

        SDField field = schema.getConcreteField("_foo_keyvalue");
        assertNotNull(field, "Expected a synthetic key-value field");
        assertEquals(DataType.getArray(DataType.STRING), field.getDataType());
    }

    @Test
    void requireKeyValueAttributeIsAFastSearchStringArray() throws ParseException {
        var schema = build(fastSearchMap("map<string, string>"));

        Attribute attribute = schema.getConcreteField("_foo_keyvalue").getAttributes().get("_foo_keyvalue");
        assertNotNull(attribute);
        assertEquals(Attribute.Type.STRING, attribute.getType());
        assertEquals(Attribute.CollectionType.ARRAY, attribute.getCollectionType());
        assertTrue(attribute.isFastSearch());
    }

    @Test
    void requireKeyValueFieldIsCreatedForIntKeysAndValues() throws ParseException {
        for (String type : new String[] { "map<int, string>", "map<string, int>", "map<int, int>" }) {
            var schema = build(fastSearchMap(type));
            assertNotNull(schema.getConcreteField("_foo_keyvalue"), "Expected key-value field for " + type);
        }
    }

    @Test
    void requireKeyValueFieldIsAddedToTheInternalFieldSet() throws ParseException {
        var schema = build(fastSearchMap("map<string, string>"));

        var internal = schema.fieldSets().builtInFieldSets().get(BuiltInFieldSets.INTERNAL_FIELDSET_NAME);
        assertNotNull(internal);
        assertTrue(internal.getFieldNames().contains("_foo_keyvalue"),
                   "Expected _foo_keyvalue in " + internal.getFieldNames());
    }

    @Test
    void requireNoKeyValueFieldWithoutFastSearch() throws ParseException {
        var schema = build(joinLines("field foo type map<string, string> {",
                                     "  indexing: summary",
                                     "}"));

        assertNull(schema.getConcreteField("_foo_keyvalue"));
    }

    @Test
    void requireNoKeyValueFieldForNonMapFields() throws ParseException {
        var schema = build(joinLines("field foo type array<string> {",
                                     "  indexing: summary",
                                     "}"));

        assertNull(schema.getConcreteField("_foo_keyvalue"));
    }

    @Test
    void requireOneKeyValueFieldPerFastSearchMap() throws ParseException {
        var schema = build(joinLines("field foo type map<string, string> {",
                                     "  indexing: summary",
                                     "  map: fast-search",
                                     "}",
                                     "field bar type map<string, string> {",
                                     "  indexing: summary",
                                     "  map: fast-search",
                                     "}",
                                     "field baz type map<string, string> {",
                                     "  indexing: summary",
                                     "}"));

        assertNotNull(schema.getConcreteField("_foo_keyvalue"));
        assertNotNull(schema.getConcreteField("_bar_keyvalue"));
        assertNull(schema.getConcreteField("_baz_keyvalue"));
    }

    /**
     * The leading underscore is what keeps the synthetic name out of reach of schema authors,
     * so that a user cannot declare a field colliding with it.
     */
    @Test
    void requireUsersCannotDeclareTheKeyValueFieldName() {
        try {
            build(joinLines("field _foo_keyvalue type array<string> {",
                            "  indexing: attribute",
                            "}"));
            throw new AssertionError("Expected exception");
        }
        catch (IllegalArgumentException | ParseException e) {
            assertTrue(e.getMessage().contains("Not a legal field name"),
                       "Unexpected message: " + e.getMessage());
        }
    }

    private static String fastSearchMap(String type) {
        return joinLines("field foo type " + type + " {",
                         "  indexing: summary",
                         "  map: fast-search",
                         "}");
    }

    private static Schema build(String fields) throws ParseException {
        var builder = new ApplicationBuilder(new TestProperties().fastMapSearch(true));
        builder.addSchema(joinLines("schema test {",
                                    "  document test {",
                                    fields,
                                    "  }",
                                    "}"));
        builder.build(true);
        return builder.getSchema();
    }

}
