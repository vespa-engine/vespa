// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.FieldPath;
import com.yahoo.document.MapDataType;
import com.yahoo.document.StructDataType;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.LongFieldValue;
import com.yahoo.document.datatypes.MapFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.language.process.ProcessingException;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.Attribute;
import com.yahoo.schema.document.Case;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.derived.TestableDeployLogger;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.searchlib.document.FastMapSearch;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.FieldValues;
import com.yahoo.vespa.indexinglanguage.expressions.ScriptExpression;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the synthetic key-value attribute created for map fields with 'fast-search-map-field'.
 *
 * @author johsol
 */
public class CreateFastMapSearchTest {
    private static String[] supportedValueTypes = { "string", "int", "long", "float", "double" };

    @Test
    void requireKeyValueFieldIsCreatedForFastSearchMap() throws ParseException {
        for (String valueType : supportedValueTypes) {
            var schema = build(fastSearchMap("foo", "string", valueType));

            SDField field = schema.getConcreteField("foo$lookup");
            assertNotNull(field, "Expected a synthetic key-value field");
            assertEquals(DataType.getArray(DataType.STRING), field.getDataType());
        }
    }

    @Test
    void requireExceptionIsThrownForUnsupportedValueType() throws ParseException {
        assertThrows(IllegalArgumentException.class, () -> { build(fastSearchMap("foo", "string", "byte")); });
    }

    @Test
    void requireKeyValueAttributeIsAFastSearchStringArray() throws ParseException {
        for (String valueType : supportedValueTypes) {
            var schema = build(fastSearchMap("foo", "string", valueType));

            Attribute attribute = schema.getConcreteField("foo$lookup").getAttributes().get("foo$lookup");
            assertNotNull(attribute);
            assertEquals(Attribute.Type.STRING, attribute.getType());
            assertEquals(Attribute.CollectionType.ARRAY, attribute.getCollectionType());
            assertTrue(attribute.isFastSearch());
        }
    }

    @Test
    void requireKeyValueAttributeIsUncasedByDefault() throws ParseException {
        for (String valueType : supportedValueTypes) {
            var schema = build(fastSearchMap("foo", "string", valueType));

            Attribute attribute = keyValueAttribute(schema, "foo");
            assertEquals(Case.UNCASED, attribute.getCase());
            assertNull(attribute.getDictionary(), "An uncased attribute keeps the default dictionary");
        }
    }

    /** A cased key and value give a cased term, which needs a cased dictionary or the lookup would be folded. */
    @Test
    void requireKeyValueAttributeIsCasedWhenTheStringMapIsCased() throws ParseException {
        var schema = build(casedFastSearchMap("foo", "string", true, true));

        Attribute attribute = keyValueAttribute(schema, "foo");
        assertEquals(Case.CASED, attribute.getCase());
        assertNotNull(attribute.getDictionary());
        assertEquals(Case.CASED, attribute.getDictionary().getMatch());
    }

    /** A numeric value is hex encoded the same way at index and query time, so only the key decides the casing. */
    @Test
    void requireKeyValueAttributeOfANumericMapFollowsTheKeyCasing() throws ParseException {
        for (String valueType : new String[] { "int", "long", "float", "double" }) {
            assertEquals(Case.CASED, keyValueAttribute(build(casedFastSearchMap("foo", valueType, true, false)), "foo").getCase());
            assertEquals(Case.UNCASED, keyValueAttribute(build(casedFastSearchMap("foo", valueType, false, false)), "foo").getCase());
        }
    }

    @Test
    void requireErrorWhenOnlyOneOfTheKeyAndValueOfAStringMapIsCased() {
        for (boolean casedKey : new boolean[] { true, false }) {
            var exception = assertThrows(IllegalArgumentException.class,
                    () -> build(casedFastSearchMap("foo", "string", casedKey, !casedKey)));
            assertTrue(exception.getMessage().contains("Map with fast search requires the same match casing " +
                                                       "on the key and the value, but only the " +
                                                       (casedKey ? "key" : "value") + " is cased."),
                    "Unexpected message: " + exception.getMessage());
        }
    }

    @Test
    void requireKeyValueAttributeHasCorrectIndexingScriptForStringValues() throws ParseException {
        var schema = build(fastSearchMap("foo", "string", "string"));

        String script = schema.getConcreteField("foo$lookup").getIndexingScript().toString();
        assertEquals("{ input foo | for_each { get_field $key . \"\\x7f\" . get_field $value } | attribute \"foo$lookup\"; }", script);
    }

    @Test
    void requireKeyValueAttributeHasCorrectIndexingScriptForIntValues() throws ParseException {
        var schema = build(fastSearchMap("foo", "string", "int"));

        String script = schema.getConcreteField("foo$lookup").getIndexingScript().toString();
        assertEquals("{ input foo | for_each { get_field $key . \"\\x7f\" . (get_field $value | exhex8encode) } | attribute \"foo$lookup\"; }", script);
    }

    @Test
    void requireKeyValueAttributeHasCorrectIndexingScriptForLongValues() throws ParseException {
        var schema = build(fastSearchMap("foo", "string", "long"));

        String script = schema.getConcreteField("foo$lookup").getIndexingScript().toString();
        assertEquals("{ input foo | for_each { get_field $key . \"\\x7f\" . (get_field $value | exhex16encode) } | attribute \"foo$lookup\"; }", script);
    }

    @Test
    void requireKeyValueAttributeHasCorrectIndexingScriptForFloatValues() throws ParseException {
        var schema = build(fastSearchMap("foo", "string", "float"));

        String script = schema.getConcreteField("foo$lookup").getIndexingScript().toString();
        assertEquals("{ input foo | for_each { get_field $key . \"\\x7f\" . (get_field $value | exhex8floatencode) } | attribute \"foo$lookup\"; }", script);
    }

    @Test
    void requireKeyValueAttributeHasCorrectIndexingScriptForDoubleValues() throws ParseException {
        var schema = build(fastSearchMap("foo", "string", "double"));

        String script = schema.getConcreteField("foo$lookup").getIndexingScript().toString();
        assertEquals("{ input foo | for_each { get_field $key . \"\\x7f\" . (get_field $value | exhex16doubleencode) } | attribute \"foo$lookup\"; }", script);
    }

    @Test
    void requireKeyValueAttributeHasCorrectIndexingScriptForIntegerKeys() throws ParseException {
        assertEquals("{ input foo | for_each { (get_field $key | to_string) . \"\\x7f\" . get_field $value } | attribute \"foo$lookup\"; }",
                     build(fastSearchMap("foo", "int", "string")).getConcreteField("foo$lookup").getIndexingScript().toString());
        assertEquals("{ input foo | for_each { (get_field $key | to_string) . \"\\x7f\" . (get_field $value | exhex8encode) } | attribute \"foo$lookup\"; }",
                     build(fastSearchMap("foo", "long", "int")).getConcreteField("foo$lookup").getIndexingScript().toString());
        assertEquals("{ input foo | for_each { (get_field mykey | to_string) . \"\\x7f\" . get_field myvalue } | attribute \"foo$lookup\"; }",
                     build(fastSearchArray("foo", "int", "string")).getConcreteField("foo$lookup").getIndexingScript().toString());
    }

    /**
     * An integer key has no casing, so the attribute follows the casing of a string value, and casing on the key
     * is ignored with the usual warning for non-string fields.
     */
    @Test
    void requireKeyValueAttributeWithIntegerKeyFollowsTheValueCasing() throws ParseException {
        Attribute attribute = keyValueAttribute(build(casedFastSearchMap("foo", "int", "string", false, true)), "foo");
        assertEquals(Case.CASED, attribute.getCase());
        assertEquals(Case.CASED, attribute.getDictionary().getMatch());

        var logger = new TestableDeployLogger();
        assertEquals(Case.UNCASED, keyValueAttribute(build(casedFastSearchMap("foo", "int", "string", true, false), logger), "foo").getCase());
        assertEquals(List.of("For schema 'test', field 'foo.key': 'match: cased' is only used for string fields, ignoring it."),
                     logger.warnings);

        logger = new TestableDeployLogger();
        assertEquals(Case.UNCASED, keyValueAttribute(build(fastSearchMap("foo", "int", "string"), logger), "foo").getCase());
        assertEquals(List.of(), logger.warnings);
        assertEquals(Case.UNCASED, keyValueAttribute(build(fastSearchMap("foo", "long", "int")), "foo").getCase());
    }

    /**
     * Runs the generated indexing script, and checks that the attribute holds the same terms as the
     * query rewrite in FastMapSearcher makes with the same FastMapSearch methods.
     */
    @Test
    void requireGeneratedScriptWritesIntegerKeysInDecimal() throws ParseException {
        var intMap = build(fastSearchMap("foo", "int", "string"));
        var intValue = new MapFieldValue<IntegerFieldValue, StringFieldValue>((MapDataType) mapType(intMap, "foo"));
        intValue.put(new IntegerFieldValue(42), new StringFieldValue("bar"));
        intValue.put(new IntegerFieldValue(-7), new StringFieldValue("baz"));
        assertEquals(List.of(FastMapSearch.toKeyValueTerm("-7", "baz"), FastMapSearch.toKeyValueTerm("42", "bar")),
                     indexedLookupValues(intMap, "foo", intValue));

        var longMap = build(fastSearchMap("foo", "long", "int"));
        var longValue = new MapFieldValue<LongFieldValue, IntegerFieldValue>((MapDataType) mapType(longMap, "foo"));
        longValue.put(new LongFieldValue(5000000000L), new IntegerFieldValue(3));
        assertEquals(List.of(FastMapSearch.toKeyValue8Term("5000000000", 3)),
                     indexedLookupValues(longMap, "foo", longValue));

        // An element without a key is not written to the attribute
        var intArray = build(fastSearchArray("foo", "int", "string"));
        var arrayType = (ArrayDataType) mapType(intArray, "foo");
        var structType = (StructDataType) arrayType.getNestedType();
        var arrayValue = new Array<Struct>(arrayType);
        arrayValue.add(entry(structType, 42, "bar"));
        arrayValue.add(entry(structType, null, "baz"));
        assertEquals(List.of(FastMapSearch.toKeyValueTerm("42", "bar")),
                     indexedLookupValues(intArray, "foo", arrayValue));
    }

    @Test
    void requireKeyValueFieldIsCreatedForFastSearchArrayOfStruct() throws ParseException {
        for (String valueType : supportedValueTypes) {
            var schema = build(fastSearchArray("foo", "string", valueType));

            SDField field = schema.getConcreteField("foo$lookup");
            assertNotNull(field, "Expected a synthetic key-value field");
            assertEquals(DataType.getArray(DataType.STRING), field.getDataType());
            Attribute attribute = keyValueAttribute(schema, "foo");
            assertTrue(attribute.isFastSearch());
            assertEquals(Case.UNCASED, attribute.getCase());
        }
    }

    @Test
    void requireKeyValueAttributeHasCorrectIndexingScriptForArrayOfStruct() throws ParseException {
        assertEquals("{ input foo | for_each { get_field mykey . \"\\x7f\" . get_field myvalue } | attribute \"foo$lookup\"; }",
                     build(fastSearchArray("foo", "string", "string")).getConcreteField("foo$lookup").getIndexingScript().toString());
        assertEquals("{ input foo | for_each { get_field mykey . \"\\x7f\" . (get_field myvalue | exhex8encode) } | attribute \"foo$lookup\"; }",
                     build(fastSearchArray("foo", "string", "int")).getConcreteField("foo$lookup").getIndexingScript().toString());
        assertEquals("{ input foo | for_each { get_field mykey . \"\\x7f\" . (get_field myvalue | exhex16encode) } | attribute \"foo$lookup\"; }",
                     build(fastSearchArray("foo", "string", "long")).getConcreteField("foo$lookup").getIndexingScript().toString());
    }

    @Test
    void requireKeyValueAttributeOfArrayOfStructFollowsTheStructFieldCasing() throws ParseException {
        var schema = build(casedFastSearchArray("foo", true, true));

        Attribute attribute = keyValueAttribute(schema, "foo");
        assertEquals(Case.CASED, attribute.getCase());
        assertEquals(Case.CASED, attribute.getDictionary().getMatch());

        var exception = assertThrows(IllegalArgumentException.class, () -> build(casedFastSearchArray("foo", true, false)));
        assertTrue(exception.getMessage().contains("but only the key is cased."), "Unexpected message: " + exception.getMessage());
    }

    @Test
    void requireKeyValueFieldIsAddedToTheInternalFieldSet() throws ParseException {
        for (String valueType : supportedValueTypes) {
            var schema = build(fastSearchMap("foo", "string", valueType));

            var internal = schema.fieldSets().builtInFieldSets().get(BuiltInFieldSets.INTERNAL_FIELDSET_NAME);
            assertNotNull(internal);
            assertTrue(internal.getFieldNames().contains("foo$lookup"),
                    "Expected foo$lookup in " + internal.getFieldNames());
        }
    }

    @Test
    void requireNoKeyValueFieldWithoutFastSearch() throws ParseException {
        for (String valueType : supportedValueTypes) {
            var schema = build(nonFastSearchMap("foo", "string", valueType));
            assertNull(schema.getConcreteField("foo$lookup"));
        }
    }

    @Test
    void requireNoKeyValueFieldForNonMapFields() throws ParseException {
        var schema = build(joinLines("field foo type array<string> {",
                                     "  indexing: summary",
                                     "}"));

        assertNull(schema.getConcreteField("foo$lookup"));
    }

    @Test
    void requireOneKeyValueFieldPerFastSearchMap() throws ParseException {
        for (String valueType : supportedValueTypes) {
            var schema = build(joinLines(fastSearchMap("foo", "string", valueType),
                                         fastSearchMap("bar", "string", valueType),
                                         nonFastSearchMap("baz", "string", valueType)));

            assertNotNull(schema.getConcreteField("foo$lookup"));
            assertNotNull(schema.getConcreteField("bar$lookup"));
            assertNull(schema.getConcreteField("baz$lookup"));
        }
    }

    /** Each lookup of a field gets its own attribute, with its own key and value. */
    @Test
    void requireOneKeyValueFieldPerLookup() throws ParseException {
        var schema = build(joinLines(entryStruct("string", "int"),
                                     "field foo type array<entry> {",
                                     "  indexing: summary",
                                     "  fast-search-map-field lookup { key: mykey value: myvalue }",
                                     "  fast-search-map-field reversed { key: myvalue value: mykey }",
                                     "}"));

        assertEquals("{ input foo | for_each { get_field mykey . \"\\x7f\" . (get_field myvalue | exhex8encode) } | attribute \"foo$lookup\"; }",
                     schema.getConcreteField("foo$lookup").getIndexingScript().toString());
        assertEquals("{ input foo | for_each { (get_field myvalue | to_string) . \"\\x7f\" . get_field mykey } | attribute \"foo$reversed\"; }",
                     schema.getConcreteField("foo$reversed").getIndexingScript().toString());
        assertNotNull(keyValueAttribute(schema, "foo"));
        assertNotNull(schema.getConcreteField("foo$reversed").getAttributes().get("foo$reversed"));
    }

    /**
     * The dollar is what keeps the synthetic name out of reach of schema authors.
     */
    @Test
    void requireUsersCannotDeclareTheKeyValueFieldName() {
        assertThrows(ParseException.class, () -> build(joinLines("field foo$lookup type array<string> {",
                        "  indexing: attribute",
                        "}")));
    }

    @Test
    void requireErrorWhenAnotherFieldCreatesTheKeyValueAttribute() {
        for (String valueType : supportedValueTypes) {
            var exception = assertThrows(IllegalArgumentException.class,
                    () -> build(joinLines(fastSearchMap("foo", "string", valueType),
                                          "field other type array<string> {",
                                          "  indexing: attribute \"foo$lookup\"",
                                          "}")));
            assertTrue(exception.getMessage().contains("Incompatible map attribute 'foo$lookup' already created"),
                    "Unexpected message: " + exception.getMessage());
        }
    }

    /**
     * A child schema sees the parent's key-value field through inheritance. It must reuse
     * that field rather than derive it again, while still deriving its own fast-search maps.
     */
    @Test
    void requireInheritedFastSearchMapIsNotDerivedAgain() throws ParseException {
        for (String valueType : supportedValueTypes) {
            var builder = new ApplicationBuilder(new TestProperties().fastMapSearch(true));
            builder.addSchema(joinLines("schema parent {",
                                        "  document parent {",
                                        fastSearchMap("foo", "string", valueType),
                                        "  }",
                                        "}"));
            builder.addSchema(joinLines("schema child inherits parent {",
                                        "  document child inherits parent {",
                                        fastSearchMap("bar", "string", valueType),
                                        "  }",
                                        "}"));
            builder.build(true);
            Schema parent = builder.getSchema("parent");
            Schema child = builder.getSchema("child");

            // The inherited key-value field is the parent's, not a copy made by the child. A copy would
            // shadow the parent's field, since own extra fields are looked up before inherited ones.
            SDField inherited = child.getConcreteField("foo$lookup");
            assertNotNull(inherited, "Expected child to see the inherited key-value field");
            assertSame(parent.getConcreteField("foo$lookup"), inherited);

            // The child's own fast-search map is still derived, and only in the child.
            assertNotNull(child.getConcreteField("bar$lookup"), "Expected key-value field for the child's own map");
            assertNull(parent.getConcreteField("bar$lookup"));
        }
    }

    private static Attribute keyValueAttribute(Schema schema, String mapName) {
        String fieldName = mapName + "$lookup";
        Attribute attribute = schema.getConcreteField(fieldName).getAttributes().get(fieldName);
        assertNotNull(attribute);
        return attribute;
    }

    private static String casedFastSearchMap(String name, String valueType, boolean casedKey, boolean casedValue) {
        return casedFastSearchMap(name, "string", valueType, casedKey, casedValue);
    }

    private static String casedFastSearchMap(String name, String keyType, String valueType, boolean casedKey, boolean casedValue) {
        return joinLines("field " + name + " type map<" + keyType + ", " + valueType + "> {",
                         "  indexing: summary",
                         "  fast-search-map-field lookup",
                         "  struct-field key {",
                         "    indexing: attribute",
                         "    match: " + (casedKey ? "cased" : "uncased"),
                         "  }",
                         "  struct-field value {",
                         "    indexing: attribute",
                         "    match: " + (casedValue ? "cased" : "uncased"),
                         "  }",
                         "}");
    }

    private static String entryStruct(String keyType, String valueType) {
        return joinLines("struct entry {",
                         "  field mykey type " + keyType + " { }",
                         "  field myvalue type " + valueType + " { }",
                         "}");
    }

    private static String fastSearchArray(String name, String keyType, String valueType) {
        return joinLines(entryStruct(keyType, valueType),
                         "field " + name + " type array<entry> {",
                         "  indexing: summary",
                         "  fast-search-map-field lookup {",
                         "    key: mykey",
                         "    value: myvalue",
                         "  }",
                         "}");
    }

    private static String casedFastSearchArray(String name, boolean casedKey, boolean casedValue) {
        return joinLines(entryStruct("string", "string"),
                         "field " + name + " type array<entry> {",
                         "  indexing: summary",
                         "  fast-search-map-field lookup {",
                         "    key: mykey",
                         "    value: myvalue",
                         "  }",
                         "  struct-field mykey {",
                         "    indexing: attribute",
                         "    match: " + (casedKey ? "cased" : "uncased"),
                         "  }",
                         "  struct-field myvalue {",
                         "    indexing: attribute",
                         "    match: " + (casedValue ? "cased" : "uncased"),
                         "  }",
                         "}");
    }

    private static String nonFastSearchMap(String name, String keyType, String valueType) {
        return joinLines("field " + name + " type map<" + keyType + ", " + valueType + "> {",
                "  indexing: summary",
                "}");
    }

    private static String fastSearchMap(String name, String keyType, String valueType) {
        return joinLines("field " + name + " type map<" + keyType + ", " + valueType + "> {",
                         "  indexing: summary",
                         "  fast-search-map-field lookup",
                         "}");
    }

    private static Struct entry(StructDataType structType, Integer key, String value) {
        var entry = new Struct(structType);
        if (key != null) {
            entry.setFieldValue("mykey", new IntegerFieldValue(key));
        }
        entry.setFieldValue("myvalue", new StringFieldValue(value));
        return entry;
    }

    private static DataType mapType(Schema schema, String mapName) {
        return schema.getConcreteField(mapName).getDataType();
    }

    /** Runs the indexing script of the lookup attribute of the given map, and returns the values it writes, sorted. */
    @SuppressWarnings("unchecked")
    private static List<String> indexedLookupValues(Schema schema, String mapName, FieldValue mapValue) {
        String lookupName = mapName + "$lookup";
        var values = new FieldValues() {
            final Map<String, FieldValue> output = new HashMap<>();
            @Override public DataType getFieldType(String fieldName, Expression expression) {
                return fieldName.equals(mapName) ? mapType(schema, mapName) : DataType.getArray(DataType.STRING);
            }
            @Override public FieldValue getInputValue(String fieldName) { return fieldName.equals(mapName) ? mapValue : null; }
            @Override public FieldValue getInputValue(FieldPath fieldPath) { return getInputValue(fieldPath.toString()); }
            @Override public FieldValues setOutputValue(String fieldName, FieldValue value, Expression expression) {
                output.put(fieldName, value);
                return this;
            }
            @Override public boolean isComplete() { return false; }
        };
        ScriptExpression script = schema.getConcreteField(lookupName).getIndexingScript();
        script.resolve(values);
        script.execute(values);
        var written = (Array<StringFieldValue>) values.output.get(lookupName);
        return written.stream().map(StringFieldValue::getString).sorted().toList();
    }

    private static Schema build(String fields) throws ParseException {
        return build(fields, new TestableDeployLogger());
    }

    private static Schema build(String fields, DeployLogger logger) throws ParseException {
        var builder = new ApplicationBuilder(MockApplicationPackage.createEmpty(), new MockFileRegistry(), logger,
                                             new TestProperties().fastMapSearch(true),
                                             new RankProfileRegistry(), new QueryProfileRegistry());
        builder.addSchema(joinLines("schema test {",
                                    "  document test {",
                                    fields,
                                    "  }",
                                    "}"));
        builder.build(true);
        return builder.getSchema();
    }

}
