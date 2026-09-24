// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.schema.derived.IndexingScript;
import com.yahoo.schema.derived.SchemaInfo;
import com.yahoo.schema.derived.Summaries;
import com.yahoo.schema.document.FastMapSearchFields;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.parser.ParseException;
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
 * Tests the 'map: fast-search' setting on a field, which is gated by the fast-map-search feature flag.
 *
 * @author johsol
 */
public class MapFastSearchTestCase {

    @Test
    void requireFastSearchIsSetWhenFlagEnabled() throws ParseException {
        assertTrue(fastMapSearchOf("field m type map<string, string> { map: fast-search }", true));
    }

    @Test
    void requireFastSearchSpecifiedAsBlock() throws ParseException {
        String field = joinLines("field m type map<string, string> {",
                                 "  map {",
                                 "    fast-search",
                                 "  }",
                                 "}");
        assertTrue(fastMapSearchOf(field, true));
    }

    @Test
    void requireFastSearchDisabledByDefault() throws ParseException {
        assertFalse(fastMapSearchOf("field m type map<string, string> { }", true));
        assertFalse(fastMapSearchOf("field m type map<string, string> { }", false));
    }

    @Test
    void requireFastMapAndPlainMapCanCoexist() throws ParseException {
        String fields = joinLines("field plain type map<string, string> { }",
                                  "field fast type map<string, string> { map: fast-search }");
        var schema = build(getSd(fields), true);
        assertFalse(fieldIn(schema, "plain").hasFastMapSearch());
        assertTrue(fieldIn(schema, "fast").hasFastMapSearch());
    }

    @Test
    void requireFastMapRejectedForNonMaps() throws ParseException {
        assertRejected("field m type int { map: fast-search }", true,
                       "For schema 'test', field 'm': 'map: fast-search' requires a map or an array of struct field, but the type is int.");
        assertRejected("field m type array<string> { map: fast-search }", true,
                       "For schema 'test', field 'm': 'map: fast-search' requires a map or an array of struct field, but the type is Array<string>.");
        assertRejected("field m type weightedset<string> { map: fast-search }", true,
                       "For schema 'test', field 'm': 'map: fast-search' requires a map or an array of struct field, but the type is WeightedSet<string>.");
    }

    @Test
    void requireFastMapRejectedForUnsupportedKeyAndValueTypes() throws ParseException {
        assertRejected("field m type map<double, string> { map: fast-search }", true,
                       "For schema 'test', field 'm': 'map: fast-search' requires key to be of type string, int or long, but the type is double.");
        assertRejected("field m type map<string, bool> { map: fast-search }", true,
                       "For schema 'test', field 'm': 'map: fast-search' requires value to be of type string, int or long, but the type is bool.");
    }

    @Test
    void requireFastMapAcceptsStringIntAndLongKeyAndValue() throws ParseException {
        assertTrue(fastMapSearchOf("field m type map<int, string> { map: fast-search }", true));
        assertTrue(fastMapSearchOf("field m type map<string, int> { map: fast-search }", true));
        assertTrue(fastMapSearchOf("field m type map<long, string> { map: fast-search }", true));
        assertTrue(fastMapSearchOf("field m type map<string, long> { map: fast-search }", true));
    }

    @Test
    void requireFastMapFieldsAreListedInIlscriptsConfig() throws ParseException {
        String fields = joinLines("field plain type map<string, string> { }",
                                  "field fast type map<string, string> { map: fast-search }",
                                  "field alsoFast type map<string, int> { map: fast-search }");
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
        assertEquals(FastMapSearchFields.MAP,
                     fieldIn(build(getSd(fieldWithMap("map<string, string>", "key: key", "value: value", "fast-search")), true), "m")
                             .getFastMapSearch());
        assertRejected(fieldWithMap("map<string, string>", "key: k", "fast-search"), true,
                       "For schema 'test', field 'm': 'map: fast-search' on a map requires key to be 'key', but got 'k'.");
    }

    @Test
    void requireFastSearchOnArrayOfStruct() throws ParseException {
        for (String valueType : new String[] { "string", "int", "long" }) {
            var field = fieldIn(build(getSdWithEntry("string", valueType,
                                                     fieldWithMap("array<entry>", "key: mykey", "value: myvalue", "fast-search")),
                                      true), "m");
            assertTrue(field.hasFastMapSearch());
            assertEquals(new FastMapSearchFields("mykey", "myvalue"), field.getFastMapSearch());
        }
    }

    @Test
    void requireFastSearchOnArrayOfStructRejectedWhenFlagDisabled() {
        assertRejectedWithEntry("string", "string", fieldWithMap("array<entry>", "key: mykey", "value: myvalue", "fast-search"), false,
                                "For schema 'test', field 'm': 'map: fast-search' is an unfinished feature that " +
                                "will not be enabled yet. Please remove this property from the field.");
    }

    @Test
    void requireFastSearchOnArrayOfStructRejectedWithoutKeyAndValue() {
        String expected = "For schema 'test', field 'm': 'map: fast-search' on an array of struct requires " +
                          "'key' and 'value' in 'map' to name the struct fields to use.";
        assertRejectedWithEntry("string", "string", "field m type array<entry> { map: fast-search }", true, expected);
        assertRejectedWithEntry("string", "string", fieldWithMap("array<entry>", "key: mykey", "fast-search"), true, expected);
        assertRejectedWithEntry("string", "string", fieldWithMap("array<entry>", "value: myvalue", "fast-search"), true, expected);
    }

    @Test
    void requireFastSearchOnArrayOfStructRejectedForUnknownStructFields() {
        assertRejectedWithEntry("string", "string", fieldWithMap("array<entry>", "key: nokey", "value: myvalue", "fast-search"), true,
                                "For schema 'test', field 'm': 'map: fast-search' requires key 'nokey' to be a field in the struct.");
        assertRejectedWithEntry("string", "string", fieldWithMap("array<entry>", "key: mykey", "value: novalue", "fast-search"), true,
                                "For schema 'test', field 'm': 'map: fast-search' requires value 'novalue' to be a field in the struct.");
    }

    @Test
    void requireFastSearchOnArrayOfStructRejectedForSameKeyAndValue() {
        assertRejectedWithEntry("string", "string", fieldWithMap("array<entry>", "key: mykey", "value: mykey", "fast-search"), true,
                                "For schema 'test', field 'm': 'map: fast-search' requires 'key' and 'value' to be different struct fields.");
    }

    @Test
    void requireFastSearchOnArrayOfStructRejectedForUnsupportedTypes() {
        assertRejectedWithEntry("double", "string", fieldWithMap("array<entry>", "key: mykey", "value: myvalue", "fast-search"), true,
                                "For schema 'test', field 'm': 'map: fast-search' requires key to be of type string, int or long, but the type is double.");
        assertRejectedWithEntry("string", "bool", fieldWithMap("array<entry>", "key: mykey", "value: myvalue", "fast-search"), true,
                                "For schema 'test', field 'm': 'map: fast-search' requires value to be of type string, int or long, but the type is bool.");
    }

    @Test
    void requireKeyAndValueRejectedWithoutFastSearch() {
        assertRejectedWithEntry("string", "string", fieldWithMap("array<entry>", "key: mykey", "value: myvalue"), true,
                                "For schema 'test', field 'm': 'key' and 'value' in 'map' are only supported together with 'fast-search'.");
    }

    @Test
    void requireUnknownMapSettingIsAParseError() {
        assertThrows(ParseException.class,
                     () -> build(getSdWithEntry("string", "string", fieldWithMap("array<entry>", "foo: mykey", "fast-search")), true));
    }

    @Test
    void requireFastMapFieldsAreExportedInSchemaInfo() throws ParseException {
        String fields = joinLines("field plain type map<string, string> { }",
                                  "field fastmap type map<string, int> { map: fast-search }",
                                  namedFieldWithMap("fastarray", "array<entry>", "key: mykey", "value: myvalue", "fast-search"));
        var schema = build(getSdWithEntry("string", "long", fields), true);
        var config = schemaInfoConfigOf(schema).schema(0);
        assertTrue(fieldConfig(config, "plain").fastMapSearch().isEmpty());

        var fastMap = fieldConfig(config, "fastmap").fastMapSearch();
        assertEquals(1, fastMap.size());
        assertEquals("key", fastMap.get(0).keyField());
        assertEquals("string", fastMap.get(0).keyType());
        assertEquals("value", fastMap.get(0).valueField());
        assertEquals("int", fastMap.get(0).valueType());

        var fastArray = fieldConfig(config, "fastarray").fastMapSearch();
        assertEquals(1, fastArray.size());
        assertEquals("mykey", fastArray.get(0).keyField());
        assertEquals("string", fastArray.get(0).keyType());
        assertEquals("myvalue", fastArray.get(0).valueField());
        assertEquals("long", fastArray.get(0).valueType());
    }

    @Test
    void requireFastArrayOfStructIsListedInIlscriptsConfig() throws ParseException {
        var config = ilscriptsConfigOf(build(getSdWithEntry("string", "string",
                                                            fieldWithMap("array<entry>", "key: mykey", "value: myvalue", "fast-search")),
                                             true));
        var complexFields = config.ilscript(0).complexfield();
        assertEquals(1, complexFields.size());
        assertEquals("m", complexFields.get(0).name());
        assertEquals(IlscriptsConfig.Ilscript.Complexfield.Why.FAST_MAP_SEARCH, complexFields.get(0).why());
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

    /** Returns a field named m of the given type, with a map block holding the given settings, one per line. */
    private static String fieldWithMap(String type, String ... mapSettings) {
        return namedFieldWithMap("m", type, mapSettings);
    }

    private static String namedFieldWithMap(String name, String type, String ... mapSettings) {
        List<String> lines = new ArrayList<>();
        lines.add("field " + name + " type " + type + " {");
        lines.add("  map {");
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
