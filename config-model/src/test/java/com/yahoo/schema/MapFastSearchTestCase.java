// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.schema.derived.IndexingScript;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.vespa.configdefinition.IlscriptsConfig;
import org.junit.jupiter.api.Test;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
                       "For schema 'test', field 'm': 'map: fast-search' requires a map field, but the type is int.");
        assertRejected("field m type array<string> { map: fast-search }", true,
                       "For schema 'test', field 'm': 'map: fast-search' requires a map field, but the type is Array<string>.");
        assertRejected("field m type weightedset<string> { map: fast-search }", true,
                       "For schema 'test', field 'm': 'map: fast-search' requires a map field, but the type is WeightedSet<string>.");
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

    private static String getSd(String fields) {
        return joinLines("schema test {",
                         "  document test {",
                         fields,
                         "  }",
                         "}");
    }

}
