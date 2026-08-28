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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the synthetic key-value attribute created for map fields with 'map: fast-search'.
 *
 * @author johsol
 */
public class CreateFastMapSearchTest {

    @Test
    void requireKeyValueFieldIsCreatedForFastSearchMap() throws ParseException {
        var schema = build(fastSearchMap("map<string, string>"));

        SDField field = schema.getConcreteField("foo$keyvalue");
        assertNotNull(field, "Expected a synthetic key-value field");
        assertEquals(DataType.getArray(DataType.STRING), field.getDataType());
    }

    @Test
    void requireKeyValueAttributeIsAFastSearchStringArray() throws ParseException {
        var schema = build(fastSearchMap("map<string, string>"));

        Attribute attribute = schema.getConcreteField("foo$keyvalue").getAttributes().get("foo$keyvalue");
        assertNotNull(attribute);
        assertEquals(Attribute.Type.STRING, attribute.getType());
        assertEquals(Attribute.CollectionType.ARRAY, attribute.getCollectionType());
        assertTrue(attribute.isFastSearch());
    }

    @Test
    void requireKeyValueFieldIsCreatedForIntKeysAndValues() throws ParseException {
        for (String type : new String[] { "map<int, string>", "map<string, int>", "map<int, int>" }) {
            var schema = build(fastSearchMap(type));
            assertNotNull(schema.getConcreteField("foo$keyvalue"), "Expected key-value field for " + type);
        }
    }

    @Test
    void requireKeyValueFieldIsAddedToTheInternalFieldSet() throws ParseException {
        var schema = build(fastSearchMap("map<string, string>"));

        var internal = schema.fieldSets().builtInFieldSets().get(BuiltInFieldSets.INTERNAL_FIELDSET_NAME);
        assertNotNull(internal);
        assertTrue(internal.getFieldNames().contains("foo$keyvalue"),
                   "Expected foo$keyvalue in " + internal.getFieldNames());
    }

    @Test
    void requireNoKeyValueFieldWithoutFastSearch() throws ParseException {
        var schema = build(joinLines("field foo type map<string, string> {",
                                     "  indexing: summary",
                                     "}"));

        assertNull(schema.getConcreteField("foo$keyvalue"));
    }

    @Test
    void requireNoKeyValueFieldForNonMapFields() throws ParseException {
        var schema = build(joinLines("field foo type array<string> {",
                                     "  indexing: summary",
                                     "}"));

        assertNull(schema.getConcreteField("foo$keyvalue"));
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

        assertNotNull(schema.getConcreteField("foo$keyvalue"));
        assertNotNull(schema.getConcreteField("bar$keyvalue"));
        assertNull(schema.getConcreteField("baz$keyvalue"));
    }

    /**
     * The dollar is what keeps the synthetic name out of reach of schema authors.
     */
    @Test
    void requireUsersCannotDeclareTheKeyValueFieldName() {
        assertThrows(ParseException.class, () -> build(joinLines("field foo$keyvalue type array<string> {",
                        "  indexing: attribute",
                        "}")));
    }

    @Test
    void requireErrorWhenAnotherFieldCreatesTheKeyValueAttribute() {
        var exception = assertThrows(IllegalArgumentException.class,
                                     () -> build(joinLines("field foo type map<string, string> {",
                                                           "  indexing: summary",
                                                           "  map: fast-search",
                                                           "}",
                                                           "field other type array<string> {",
                                                           "  indexing: attribute \"foo$keyvalue\"",
                                                           "}")));
        assertTrue(exception.getMessage().contains("Incompatible map attribute 'foo$keyvalue' already created"),
                   "Unexpected message: " + exception.getMessage());
    }

    /**
     * A child schema sees the parent's key-value field through inheritance. It must reuse
     * that field rather than derive it again, while still deriving its own fast-search maps.
     */
    @Test
    void requireInheritedFastSearchMapIsNotDerivedAgain() throws ParseException {
        var builder = new ApplicationBuilder(new TestProperties().fastMapSearch(true));
        builder.addSchema(joinLines("schema parent {",
                                    "  document parent {",
                                    fastSearchMap("foo", "string"),
                                    "  }",
                                    "}"));
        builder.addSchema(joinLines("schema child inherits parent {",
                                    "  document child inherits parent {",
                                    fastSearchMap("bar", "string"),
                                    "  }",
                                    "}"));
        builder.build(true);
        Schema parent = builder.getSchema("parent");
        Schema child = builder.getSchema("child");

        // The inherited key-value field is the parent's, not a copy made by the child. A copy would
        // shadow the parent's field, since own extra fields are looked up before inherited ones.
        SDField inherited = child.getConcreteField("foo$keyvalue");
        assertNotNull(inherited, "Expected child to see the inherited key-value field");
        assertSame(parent.getConcreteField("foo$keyvalue"), inherited);

        // The child's own fast-search map is still derived, and only in the child.
        assertNotNull(child.getConcreteField("bar$keyvalue"), "Expected key-value field for the child's own map");
        assertNull(parent.getConcreteField("bar$keyvalue"));
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
