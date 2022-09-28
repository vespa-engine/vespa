// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.schema.processing;

import com.yahoo.config.model.test.TestUtil;
import com.yahoo.schema.Schema;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.derived.AttributeFields;
import com.yahoo.schema.document.Case;
import com.yahoo.schema.document.Dictionary;
import com.yahoo.schema.document.ImmutableSDField;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.vespa.config.search.AttributesConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test configuration of dictionary control.
 *
 * @author baldersheim
 */
public class DictionaryTestCase {
    private static AttributesConfig getConfig(Schema schema) {
        AttributeFields attributes = new AttributeFields(schema);
        AttributesConfig.Builder builder = new AttributesConfig.Builder();
        attributes.getConfig(builder, AttributeFields.FieldSet.ALL, 130000);
        return builder.build();
    }
    private Schema createSearch(String def) throws ParseException {
        ApplicationBuilder sb = ApplicationBuilder.createFromString(def);
        return sb.getSchema();
    }

    @Test
    void testDefaultDictionarySettings() throws ParseException {
        String def = TestUtil.joinLines(
                "search test {",
                "    document test {",
                "        field s1 type string {",
                "            indexing: attribute | summary",
                "        }",
                "        field n1 type int {",
                "            indexing: summary | attribute",
                "        }",
                "    }",
                "}");
        Schema schema = createSearch(def);
        assertNull(schema.getAttribute("s1").getDictionary());
        assertNull(schema.getAttribute("n1").getDictionary());
        assertEquals(AttributesConfig.Attribute.Dictionary.Type.BTREE,
                getConfig(schema).attribute().get(0).dictionary().type());
        assertEquals(AttributesConfig.Attribute.Dictionary.Type.BTREE,
                getConfig(schema).attribute().get(1).dictionary().type());
        assertEquals(AttributesConfig.Attribute.Dictionary.Match.UNCASED,
                getConfig(schema).attribute().get(0).dictionary().match());
        assertEquals(AttributesConfig.Attribute.Dictionary.Match.UNCASED,
                getConfig(schema).attribute().get(1).dictionary().match());
    }

    Schema verifyDictionaryControl(Dictionary.Type expected, String type, String ... cfg) throws ParseException
    {
        String def = TestUtil.joinLines(
                "schema test {",
                "    document test {",
                "        field n1 type " + type + " {",
                "            indexing: summary | attribute",
                "            attribute:fast-search",
                TestUtil.joinLines(cfg),
                "        }",
                "    }",
                "}");
        Schema schema = createSearch(def);
        AttributesConfig.Attribute.Dictionary.Type.Enum expectedConfig = toCfg(expected);
        assertEquals(expected, schema.getAttribute("n1").getDictionary().getType());
        assertEquals(expectedConfig, getConfig(schema).attribute().get(0).dictionary().type());
        return schema;
    }

    AttributesConfig.Attribute.Dictionary.Type.Enum toCfg(Dictionary.Type v) {
        return (v == Dictionary.Type.HASH)
                ? AttributesConfig.Attribute.Dictionary.Type.Enum.HASH
                : (v == Dictionary.Type.BTREE)
                    ? AttributesConfig.Attribute.Dictionary.Type.Enum.BTREE
                    : AttributesConfig.Attribute.Dictionary.Type.Enum.BTREE_AND_HASH;
    }
    AttributesConfig.Attribute.Dictionary.Match.Enum toCfg(Case v) {
        return (v == Case.CASED)
                ? AttributesConfig.Attribute.Dictionary.Match.Enum.CASED
                : AttributesConfig.Attribute.Dictionary.Match.Enum.UNCASED;
    }

    void verifyStringDictionaryControl(Dictionary.Type expectedType, Case expectedCase, Case matchCasing,
                                       String ... cfg) throws ParseException
    {
        Schema schema = verifyDictionaryControl(expectedType, "string", cfg);
        ImmutableSDField f = schema.getField("n1");
        AttributesConfig.Attribute.Dictionary.Match.Enum expectedCaseCfg = toCfg(expectedCase);
        assertEquals(matchCasing, f.getMatching().getCase());
        assertEquals(expectedCase, schema.getAttribute("n1").getDictionary().getMatch());
        assertEquals(expectedCaseCfg, getConfig(schema).attribute().get(0).dictionary().match());
    }

    @Test
    void testCasedBtreeSettings() throws ParseException {
        verifyDictionaryControl(Dictionary.Type.BTREE, "int", "dictionary:cased");
    }

    @Test
    void testNumericBtreeSettings() throws ParseException {
        verifyDictionaryControl(Dictionary.Type.BTREE, "int", "dictionary:btree");
    }

    @Test
    void testNumericHashSettings() throws ParseException {
        verifyDictionaryControl(Dictionary.Type.HASH, "int", "dictionary:hash");
    }

    @Test
    void testNumericBtreeAndHashSettings() throws ParseException {
        verifyDictionaryControl(Dictionary.Type.BTREE_AND_HASH, "int", "dictionary:btree", "dictionary:hash");
    }

    @Test
    void testNumericArrayBtreeAndHashSettings() throws ParseException {
        verifyDictionaryControl(Dictionary.Type.BTREE_AND_HASH, "array<int>", "dictionary:btree", "dictionary:hash");
    }

    @Test
    void testNumericWSetBtreeAndHashSettings() throws ParseException {
        verifyDictionaryControl(Dictionary.Type.BTREE_AND_HASH, "weightedset<int>", "dictionary:btree", "dictionary:hash");
    }

    @Test
    void testStringBtreeSettings() throws ParseException {
        verifyStringDictionaryControl(Dictionary.Type.BTREE, Case.UNCASED, Case.UNCASED, "dictionary:btree");
    }

    @Test
    void testStringBtreeUnCasedSettings() throws ParseException {
        verifyStringDictionaryControl(Dictionary.Type.BTREE, Case.UNCASED, Case.UNCASED, "dictionary { btree\nuncased\n}");
    }

    @Test
    void testStringBtreeCasedSettings() throws ParseException {
        verifyStringDictionaryControl(Dictionary.Type.BTREE, Case.CASED, Case.CASED, "dictionary { btree\ncased\n}", "match:cased");
    }

    @Test
    void testStringHashSettings() throws ParseException {
        try {
            verifyStringDictionaryControl(Dictionary.Type.HASH, Case.UNCASED, Case.UNCASED, "dictionary:hash");
        } catch (IllegalArgumentException e) {
            assertEquals("For schema 'test', field 'n1': hash dictionary require cased match", e.getMessage());
        }
    }

    @Test
    void testStringHashUnCasedSettings() throws ParseException {
        try {
            verifyStringDictionaryControl(Dictionary.Type.HASH, Case.UNCASED, Case.UNCASED, "dictionary { hash\nuncased\n}");
        } catch (IllegalArgumentException e) {
            assertEquals("For schema 'test', field 'n1': hash dictionary require cased match", e.getMessage());
        }
    }

    @Test
    void testStringHashBothCasedSettings() throws ParseException {
        verifyStringDictionaryControl(Dictionary.Type.HASH, Case.CASED, Case.CASED, "dictionary { hash\ncased\n}", "match:cased");
    }

    @Test
    void testStringHashCasedSettings() throws ParseException {
        try {
            verifyStringDictionaryControl(Dictionary.Type.HASH, Case.CASED, Case.CASED, "dictionary { hash\ncased\n}");
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("For schema 'test', field 'n1': Dictionary casing 'CASED' does not match field match casing 'UNCASED'", e.getMessage());
        }
    }

    @Test
    void testStringBtreeHashSettings() throws ParseException {
        verifyStringDictionaryControl(Dictionary.Type.BTREE_AND_HASH, Case.UNCASED, Case.UNCASED, "dictionary{hash\nbtree\n}");
    }

    @Test
    void testStringBtreeHashUnCasedSettings() throws ParseException {
        verifyStringDictionaryControl(Dictionary.Type.BTREE_AND_HASH, Case.UNCASED, Case.UNCASED, "dictionary { hash\nbtree\nuncased\n}");
    }

    @Test
    void testStringBtreeHashCasedSettings() throws ParseException {
        try {
            verifyStringDictionaryControl(Dictionary.Type.BTREE_AND_HASH, Case.CASED, Case.CASED, "dictionary { btree\nhash\ncased\n}");
        } catch (IllegalArgumentException e) {
            assertEquals("For schema 'test', field 'n1': Dictionary casing 'CASED' does not match field match casing 'UNCASED'", e.getMessage());
        }
    }

    @Test
    void testNonNumericFieldsFailsDictionaryControl() throws ParseException {
        String def = TestUtil.joinLines(
                "schema test {",
                "    document test {",
                "        field n1 type bool {",
                "            indexing: summary | attribute",
                "            dictionary:btree",
                "        }",
                "    }",
                "}");
        try {
            ApplicationBuilder.createFromString(def);
            fail("Controlling dictionary for non-numeric fields are not yet supported.");
        } catch (IllegalArgumentException e) {
            assertEquals("For schema 'test', field 'n1': You can only specify 'dictionary:' for numeric or string fields", e.getMessage());
        }
    }

    @Test
    void testNonFastSearchNumericFieldsFailsDictionaryControl() throws ParseException {
        String def = TestUtil.joinLines(
                "schema test {",
                "    document test {",
                "        field n1 type int {",
                "            indexing: summary | attribute",
                "            dictionary:btree",
                "        }",
                "    }",
                "}");
        try {
            ApplicationBuilder.createFromString(def);
            fail("Controlling dictionary for non-fast-search fields are not allowed.");
        } catch (IllegalArgumentException e) {
            assertEquals("For schema 'test', field 'n1': You must specify 'attribute:fast-search' to allow dictionary control", e.getMessage());
        }
    }

    @Test
    void testCasingForNonFastSearch() throws ParseException {
        String def = TestUtil.joinLines(
                "schema test {",
                "    document test {",
                "        field s1 type string {",
                "            indexing: attribute | summary",
                "        }",
                "        field s2 type string {",
                "            indexing: attribute | summary",
                "            match:uncased",
                "        }",
                "        field s3 type string {",
                "            indexing: attribute | summary",
                "            match:cased",
                "        }",
                "    }",
                "}");
        Schema schema = createSearch(def);
        assertEquals(Case.UNCASED, schema.getAttribute("s1").getCase());
        assertEquals(Case.UNCASED, schema.getAttribute("s2").getCase());
        assertEquals(Case.CASED, schema.getAttribute("s3").getCase());
        assertEquals(AttributesConfig.Attribute.Match.UNCASED, getConfig(schema).attribute().get(0).match());
        assertEquals(AttributesConfig.Attribute.Match.UNCASED, getConfig(schema).attribute().get(1).match());
        assertEquals(AttributesConfig.Attribute.Match.CASED, getConfig(schema).attribute().get(2).match());
    }
}
