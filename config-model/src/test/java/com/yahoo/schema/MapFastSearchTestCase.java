// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.schema.derived.IndexInfo;
import com.yahoo.schema.derived.IndexingScript;
import com.yahoo.schema.derived.SchemaInfo;
import com.yahoo.schema.derived.Summaries;
import com.yahoo.schema.document.FastMapSearchFields;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.search.config.SchemaInfoConfig;
import com.yahoo.vespa.configdefinition.IlscriptsConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests the 'fast-search map field' setting on a field, which is gated by the fast-map-search feature flag.
 *
 * @author johsol
 */
public class MapFastSearchTestCase {

    @Test
    void requireFastSearchIsSetWhenFlagEnabled() throws ParseException {
        assertTrue(fastMapSearchOf("field m type map<string, string> { fast-search map field: lookup }", true));
    }

    @Test
    void requireFastSearchSpecifiedWithBlock() throws ParseException {
        String field = joinLines("field m type map<string, string> {",
                                 "  fast-search map field: lookup {",
                                 "  }",
                                 "}");
        assertTrue(fastMapSearchOf(field, true));
        assertEquals("lookup", fieldIn(build(getSd(field), true), "m").getFastMapSearch().lookupName());
    }

    @Test
    void requireOldSyntaxIsAParseError() {
        assertThrows(ParseException.class,
                     () -> build(getSd("field m type map<string, string> { map: fast-search }"), true));
        assertThrows(ParseException.class,
                     () -> build(getSdWithEntry("string", "string", joinLines("field m type array<entry> {",
                                                                              "  map {",
                                                                              "    key: mykey",
                                                                              "    value: myvalue",
                                                                              "    fast-search",
                                                                              "  }",
                                                                              "}")), true));
    }

    @Test
    void requireRepeatedFastSearchMapFieldIsAParseError() {
        var exception = assertThrows(ParseException.class,
                                     () -> build(getSd(joinLines("field m type map<string, string> {",
                                                                 "  fast-search map field: lookup",
                                                                 "  fast-search map field: other",
                                                                 "}")), true));
        assertTrue(exception.getMessage().contains("'fast-search map field' is given more than once in field 'm'."),
                   "Unexpected message: " + exception.getMessage());
    }

    @Test
    void requireFastSearchDisabledByDefault() throws ParseException {
        assertFalse(fastMapSearchOf("field m type map<string, string> { }", true));
        assertFalse(fastMapSearchOf("field m type map<string, string> { }", false));
    }

    @Test
    void requireFastMapAndPlainMapCanCoexist() throws ParseException {
        String fields = joinLines("field plain type map<string, string> { }",
                                  "field fast type map<string, string> { fast-search map field: lookup }");
        var schema = build(getSd(fields), true);
        assertFalse(fieldIn(schema, "plain").hasFastMapSearch());
        assertTrue(fieldIn(schema, "fast").hasFastMapSearch());
    }

    @Test
    void requireFastMapRejectedForNonMaps() throws ParseException {
        assertRejected("field m type int { fast-search map field: lookup }", true,
                       "For schema 'test', field 'm': 'fast-search map field' requires a map or an array of struct field, but the type is int.");
        assertRejected("field m type array<string> { fast-search map field: lookup }", true,
                       "For schema 'test', field 'm': 'fast-search map field' requires a map or an array of struct field, but the type is Array<string>.");
        assertRejected("field m type weightedset<string> { fast-search map field: lookup }", true,
                       "For schema 'test', field 'm': 'fast-search map field' requires a map or an array of struct field, but the type is WeightedSet<string>.");
    }

    @Test
    void requireFastMapRejectedForUnsupportedKeyAndValueTypes() throws ParseException {
        assertRejected("field m type map<double, string> { fast-search map field: lookup }", true,
                       "For schema 'test', field 'm': 'fast-search map field' requires key to be of type string, but the type is double.");
        assertRejected("field m type map<int, string> { fast-search map field: lookup }", true,
                       "For schema 'test', field 'm': 'fast-search map field' requires key to be of type string, but the type is int.");
        assertRejected("field m type map<long, string> { fast-search map field: lookup }", true,
                       "For schema 'test', field 'm': 'fast-search map field' requires key to be of type string, but the type is long.");
        assertRejected("field m type map<string, bool> { fast-search map field: lookup }", true,
                       "For schema 'test', field 'm': 'fast-search map field' requires value to be of type string, int, long, float or double, but the type is bool.");
    }

    @Test
    void requireFastMapAcceptsSupportedKeyAndValueTypes() throws ParseException {
        assertTrue(fastMapSearchOf("field m type map<string, int> { fast-search map field: lookup }", true));
        assertTrue(fastMapSearchOf("field m type map<string, long> { fast-search map field: lookup }", true));
        assertTrue(fastMapSearchOf("field m type map<string, float> { fast-search map field: lookup }", true));
        assertTrue(fastMapSearchOf("field m type map<string, double> { fast-search map field: lookup }", true));
    }

    @Test
    void requireFastMapFieldsAreListedInIlscriptsConfig() throws ParseException {
        String fields = joinLines("field plain type map<string, string> { }",
                                  "field fast type map<string, string> { fast-search map field: lookup }",
                                  "field alsoFast type map<string, int> { fast-search map field: lookup }");
        var config = ilscriptsConfigOf(build(getSd(fields), true));
        assertEquals(1, config.ilscript().size());
        var complexFields = config.ilscript(0).complexfield();
        assertEquals(2, complexFields.size());
        // Sorted by field name to keep the config stable.
        assertEquals("alsoFast", complexFields.get(0).name());
        assertEquals(IlscriptsConfig.Ilscript.Complexfield.Why.FAST_MAP_SEARCH, complexFields.get(0).why());
        assertEquals("fast", complexFields.get(1).name());
        assertEquals(IlscriptsConfig.Ilscript.Complexfield.Why.FAST_MAP_SEARCH, complexFields.get(1).why());
    }

    @Test
    void requireNoComplexFieldsInIlscriptsConfigWithoutFastMapSearch() throws ParseException {
        var config = ilscriptsConfigOf(build(getSd("field m type map<string, string> { }"), true));
        assertEquals(1, config.ilscript().size());
        assertTrue(config.ilscript(0).complexfield().isEmpty());
    }

    @Test
    void requireMapKeyAndValueMayBeNamedButNotChanged() throws ParseException {
        var field = fieldIn(build(getSd(fieldWithLookup("map<string, string>", "key: key", "value: value")), true), "m");
        assertEquals(FastMapSearchFields.forMap(field.getDataType(), "lookup"), field.getFastMapSearch());
        assertRejected(fieldWithLookup("map<string, string>", "key: k"), true,
                       "For schema 'test', field 'm': 'fast-search map field' on a map requires key to be 'key', but got 'k'.");
    }

    @Test
    void requireFastSearchOnArrayOfStruct() throws ParseException {
        for (String valueType : new String[] { "string", "int", "long" }) {
            var field = fieldIn(build(getSdWithEntry("string", valueType,
                                                     fieldWithLookup("array<entry>", "key: mykey", "value: myvalue")),
                                      true), "m");
            assertTrue(field.hasFastMapSearch());
            assertEquals(FastMapSearchFields.forArrayOfStruct(field.getDataType(), "lookup", "mykey", "myvalue"), field.getFastMapSearch());
        }
    }

    @Test
    void requireFastSearchOnArrayOfStructRejectedWhenFlagDisabled() {
        assertRejectedWithEntry("string", "string", fieldWithLookup("array<entry>", "key: mykey", "value: myvalue"), false,
                                "For schema 'test', field 'm': 'fast-search map field' is an unfinished feature that " +
                                "will not be enabled yet. Please remove this property from the field.");
    }

    @Test
    void requireFastSearchOnArrayOfStructRejectedWithoutKeyAndValue() {
        String expected = "For schema 'test', field 'm': 'fast-search map field' on an array of struct requires " +
                          "'key' and 'value' in its block to name the struct fields to use.";
        assertRejectedWithEntry("string", "string", "field m type array<entry> { fast-search map field: lookup }", true, expected);
        assertRejectedWithEntry("string", "string", fieldWithLookup("array<entry>", "key: mykey"), true, expected);
        assertRejectedWithEntry("string", "string", fieldWithLookup("array<entry>", "value: myvalue"), true, expected);
    }

    @Test
    void requireFastSearchOnArrayOfStructRejectedForUnknownStructFields() {
        assertRejectedWithEntry("string", "string", fieldWithLookup("array<entry>", "key: nokey", "value: myvalue"), true,
                                "For schema 'test', field 'm': 'fast-search map field' requires key 'nokey' to be a field in the struct.");
        assertRejectedWithEntry("string", "string", fieldWithLookup("array<entry>", "key: mykey", "value: novalue"), true,
                                "For schema 'test', field 'm': 'fast-search map field' requires value 'novalue' to be a field in the struct.");
    }

    @Test
    void requireFastSearchOnArrayOfStructRejectedForSameKeyAndValue() {
        assertRejectedWithEntry("string", "string", fieldWithLookup("array<entry>", "key: mykey", "value: mykey"), true,
                                "For schema 'test', field 'm': 'fast-search map field' requires 'key' and 'value' to be different struct fields.");
    }

    @Test
    void requireFastSearchOnArrayOfStructRejectedForUnsupportedTypes() {
        assertRejectedWithEntry("double", "string", fieldWithLookup("array<entry>", "key: mykey", "value: myvalue"), true,
                                "For schema 'test', field 'm': 'fast-search map field' requires mykey to be of type string, but the type is double.");
        assertRejectedWithEntry("int", "string", fieldWithLookup("array<entry>", "key: mykey", "value: myvalue"), true,
                                "For schema 'test', field 'm': 'fast-search map field' requires mykey to be of type string, but the type is int.");
        assertRejectedWithEntry("string", "bool", fieldWithLookup("array<entry>", "key: mykey", "value: myvalue"), true,
                                "For schema 'test', field 'm': 'fast-search map field' requires myvalue to be of type string, int, long, float or double, but the type is bool.");
    }

    @Test
    void requireUnknownMapSettingIsAParseError() {
        assertThrows(ParseException.class,
                     () -> build(getSdWithEntry("string", "string", fieldWithLookup("array<entry>", "foo: mykey")), true));
    }

    @Test
    void requireRepeatedKeyOrValueIsAParseError() {
        var exception = assertThrows(ParseException.class,
                                     () -> build(getSdWithEntry("string", "string",
                                                                fieldWithLookup("array<entry>", "key: mykey", "value: myvalue",
                                                                             "key: myvalue")), true));
        assertTrue(exception.getMessage().contains("'key' is given more than once in 'fast-search map field' of field 'm'."),
                   "Unexpected message: " + exception.getMessage());
        exception = assertThrows(ParseException.class,
                                 () -> build(getSdWithEntry("string", "string",
                                                            fieldWithLookup("array<entry>", "key: mykey", "value: myvalue",
                                                                         "value: myvalue")), true));
        assertTrue(exception.getMessage().contains("'value' is given more than once in 'fast-search map field' of field 'm'."),
                   "Unexpected message: " + exception.getMessage());
    }

    @Test
    void requireFastMapFieldsAreExportedInSchemaInfo() throws ParseException {
        String fields = joinLines("field plain type map<string, string> { }",
                                  "field fastmap type map<string, int> { fast-search map field: lookup }",
                                  namedFieldWithLookup("fastarray", "array<entry>", "key: mykey", "value: myvalue"));
        var schema = build(getSdWithEntry("string", "long", fields), true);
        var config = schemaInfoConfigOf(schema).schema(0);
        assertTrue(fieldConfig(config, "plain").fastMapSearchFields().isEmpty());

        var fastMap = fieldConfig(config, "fastmap").fastMapSearchFields();
        assertEquals(1, fastMap.size());
        assertEquals("lookup", fastMap.get(0).lookupName());
        assertEquals("key", fastMap.get(0).keyField());
        assertEquals("string", fastMap.get(0).keyType());
        assertEquals("value", fastMap.get(0).valueField());
        assertEquals("int", fastMap.get(0).valueType());

        var fastArray = fieldConfig(config, "fastarray").fastMapSearchFields();
        assertEquals(1, fastArray.size());
        assertEquals("lookup", fastArray.get(0).lookupName());
        assertEquals("mykey", fastArray.get(0).keyField());
        assertEquals("string", fastArray.get(0).keyType());
        assertEquals("myvalue", fastArray.get(0).valueField());
        assertEquals("long", fastArray.get(0).valueType());
    }

    @Test
    void requireFastArrayOfStructIsListedInIlscriptsConfig() throws ParseException {
        var config = ilscriptsConfigOf(build(getSdWithEntry("string", "string",
                                                            fieldWithLookup("array<entry>", "key: mykey", "value: myvalue")),
                                             true));
        var complexFields = config.ilscript(0).complexfield();
        assertEquals(1, complexFields.size());
        assertEquals("m", complexFields.get(0).name());
        assertEquals(IlscriptsConfig.Ilscript.Complexfield.Why.FAST_MAP_SEARCH, complexFields.get(0).why());
    }

    /** The lookup field and its key and value are given the index settings of the field and its key and value. */
    @Test
    void requireLookupFieldsAreListedInIndexInfo() throws ParseException {
        String fields = joinLines("field fastmap type map<string, int> {",
                                  "  fast-search map field: lookup",
                                  "  struct-field key { indexing: attribute }",
                                  "  struct-field value { indexing: attribute }",
                                  "}",
                                  namedFieldWithLookup("fastarray", "array<entry>", "key: mykey", "value: myvalue"));
        var indexInfo = indexInfoOf(build(getSdWithEntry("string", "long", fields), true));

        assertFalse(commandsOf(indexInfo, "fastmap.key").isEmpty());
        assertEquals(commandsOf(indexInfo, "fastmap"), commandsOf(indexInfo, "fastmap.lookup"));
        assertEquals(commandsOf(indexInfo, "fastmap.key"), commandsOf(indexInfo, "fastmap.lookup.key"));
        assertEquals(commandsOf(indexInfo, "fastmap.value"), commandsOf(indexInfo, "fastmap.lookup.value"));
        assertTrue(commandsOf(indexInfo, "fastmap.lookup.key").contains("lowercase"));

        assertFalse(commandsOf(indexInfo, "fastarray.mykey").isEmpty());
        assertEquals(commandsOf(indexInfo, "fastarray"), commandsOf(indexInfo, "fastarray.lookup"));
        assertEquals(commandsOf(indexInfo, "fastarray.mykey"), commandsOf(indexInfo, "fastarray.lookup.key"));
        assertEquals(commandsOf(indexInfo, "fastarray.myvalue"), commandsOf(indexInfo, "fastarray.lookup.value"));
    }

    @Test
    void requireLookupFieldIsNotNamedKeyOrValue() throws ParseException {
        for (String name : List.of("key", "value")) {
            String expected = "For schema 'test', field 'm': 'fast-search map field' can not be named '" + name +
                              "', as 'key' and 'value' name the key and value in a lookup.";
            assertRejected("field m type map<string, string> { fast-search map field: " + name + " }", true, expected);
            assertRejectedWithEntry("string", "string",
                                    joinLines("field m type array<entry> {",
                                              "  fast-search map field: " + name + " { key: mykey value: myvalue }",
                                              "}"), true, expected);
        }
    }

    @Test
    void requireLookupFieldNameDiffersFromTheStructFields() throws ParseException {
        assertRejectedWithEntry("string", "string",
                                joinLines("field m type array<entry> {",
                                          "  fast-search map field: mykey { key: mykey value: myvalue }",
                                          "}"), true,
                                "For schema 'test', field 'm': 'fast-search map field' can not be named 'mykey', which is the name of a field in the struct.");
    }

    private static IndexInfoConfig indexInfoOf(Schema schema) {
        var builder = new IndexInfoConfig.Builder();
        new IndexInfo(schema, false).getConfig(builder);
        return builder.build();
    }

    /** Returns the commands of the given index, in order. */
    private static List<String> commandsOf(IndexInfoConfig config, String index) {
        return config.indexinfo(0).command().stream().filter(c -> c.indexname().equals(index)).map(c -> c.command()).toList();
    }

    private static SchemaInfoConfig schemaInfoConfigOf(Schema schema) {
        var schemaInfo = new SchemaInfo(schema, SchemaInfo.IndexMode.INDEX, new RankProfileRegistry(),
                                        new Summaries(schema, new BaseDeployLogger(), new TestProperties()));
        var builder = new SchemaInfoConfig.Builder();
        schemaInfo.getConfig(builder);
        return builder.build();
    }

    private static SchemaInfoConfig.Schema.Field fieldConfig(SchemaInfoConfig.Schema schema, String name) {
        return schema.field().stream().filter(f -> f.name().equals(name)).findFirst().orElseThrow();
    }

    private static IlscriptsConfig ilscriptsConfigOf(Schema schema) {
        var builder = new IlscriptsConfig.Builder();
        new IndexingScript(schema, false).getConfig(builder);
        return builder.build();
    }

    private static void assertRejected(String field, boolean flagEnabled, String expectedMessage) throws ParseException {
        try {
            build(getSd(field), flagEnabled);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals(expectedMessage, e.getMessage());
        }
    }

    private static boolean fastMapSearchOf(String field, boolean flagEnabled) throws ParseException {
        return fieldIn(build(getSd(field), flagEnabled), "m").hasFastMapSearch();
    }

    private static SDField fieldIn(Schema schema, String fieldName) {
        return (SDField) schema.getDocument().getField(fieldName);
    }

    private static Schema build(String sd, boolean flagEnabled) throws ParseException {
        var builder = new ApplicationBuilder(new TestProperties().fastMapSearch(flagEnabled));
        builder.addSchema(sd);
        builder.build(true);
        return builder.getSchema();
    }

    /**
     * Returns a field named m of the given type, with a 'fast-search map field: lookup' block holding the
     * given settings, one per line.
     */
    private static String fieldWithLookup(String type, String ... mapSettings) {
        return namedFieldWithLookup("m", type, mapSettings);
    }

    private static String namedFieldWithLookup(String name, String type, String ... mapSettings) {
        List<String> lines = new ArrayList<>();
        lines.add("field " + name + " type " + type + " {");
        lines.add("  fast-search map field: lookup {");
        for (String setting : mapSettings)
            lines.add("    " + setting);
        lines.add("  }");
        lines.add("}");
        return String.join("\n", lines);
    }

    private static void assertRejectedWithEntry(String keyType, String valueType, String field, boolean flagEnabled,
                                                String expectedMessage) {
        var exception = assertThrows(IllegalArgumentException.class,
                                     () -> build(getSdWithEntry(keyType, valueType, field), flagEnabled));
        assertEquals(expectedMessage, exception.getMessage());
    }

    private static String getSdWithEntry(String keyType, String valueType, String fields) {
        return getSd(joinLines("struct entry {",
                               "  field mykey type " + keyType + " { }",
                               "  field myvalue type " + valueType + " { }",
                               "}",
                               fields));
    }

    private static String getSd(String fields) {
        return joinLines("schema test {",
                         "  document test {",
                         fields,
                         "  }",
                         "}");
    }

}
