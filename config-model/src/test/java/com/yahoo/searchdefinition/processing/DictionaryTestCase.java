// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.searchdefinition.processing;

import com.yahoo.config.model.test.TestUtil;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.derived.AttributeFields;
import com.yahoo.searchdefinition.document.Dictionary;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.vespa.config.search.AttributesConfig;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Test configuration of dictionary control.
 *
 * @author baldersheim
 */
public class DictionaryTestCase {
    private static AttributesConfig getConfig(Search search) {
        AttributeFields attributes = new AttributeFields(search);
        AttributesConfig.Builder builder = new AttributesConfig.Builder();
        attributes.getConfig(builder);
        return builder.build();
    }
    private Search createSearch(String def) throws ParseException {
        SearchBuilder sb = SearchBuilder.createFromString(def);
        return sb.getSearch();
    }
    @Test
    public void testDefaultDictionarySettings() throws ParseException {
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
        Search search = createSearch(def);
        assertEquals(Dictionary.Type.BTREE, search.getAttribute("s1").getDictionary().getType());
        assertEquals(Dictionary.Type.BTREE, search.getAttribute("n1").getDictionary().getType());
    }

    void verifyNumericDictionaryControl(Dictionary.Type expected,
                                        AttributesConfig.Attribute.Dictionary.Type.Enum expectedConfig,
                                        String type,
                                        String ... cfg) throws ParseException
    {
        String def = TestUtil.joinLines(
                "search test {",
                "    document test {",
                "        field n1 type " + type + " {",
                "            indexing: summary | attribute",
                "            attribute:fast-search",
                TestUtil.joinLines(cfg),
                "        }",
                "    }",
                "}");
        Search search = createSearch(def);
        assertEquals(expected, search.getAttribute("n1").getDictionary().getType());
        assertEquals(expectedConfig,
                getConfig(search).attribute().get(0).dictionary().type());
    }

    @Test
    public void testNumericBtreeSettings() throws ParseException {
        verifyNumericDictionaryControl(Dictionary.Type.BTREE,
                AttributesConfig.Attribute.Dictionary.Type.BTREE,
                "int",
                "dictionary:btree");
    }
    @Test
    public void testNumericHashSettings() throws ParseException {
        verifyNumericDictionaryControl(Dictionary.Type.HASH,
                AttributesConfig.Attribute.Dictionary.Type.HASH,
                "int",
                "dictionary:hash");
    }
    @Test
    public void testNumericBtreeAndHashSettings() throws ParseException {
        verifyNumericDictionaryControl(Dictionary.Type.BTREE_AND_HASH,
                AttributesConfig.Attribute.Dictionary.Type.BTREE_AND_HASH,
                "int",
                "dictionary:btree", "dictionary:hash");
    }
    @Test
    public void testNumericArrayBtreeAndHashSettings() throws ParseException {
        verifyNumericDictionaryControl(Dictionary.Type.BTREE_AND_HASH,
                AttributesConfig.Attribute.Dictionary.Type.BTREE_AND_HASH,
                "array<int>",
                "dictionary:btree", "dictionary:hash");
    }
    @Test
    public void testNumericWSetBtreeAndHashSettings() throws ParseException {
        verifyNumericDictionaryControl(Dictionary.Type.BTREE_AND_HASH,
                AttributesConfig.Attribute.Dictionary.Type.BTREE_AND_HASH,
                "weightedset<int>",
                "dictionary:btree", "dictionary:hash");
    }
    @Test
    public void testNonNumericFieldsFailsDictionaryControl() throws ParseException {
        String def =
                "search test {\n" +
                        "    document test {\n" +
                        "        field n1 type string {\n" +
                        "            indexing: summary | attribute\n" +
                        "            dictionary:btree\n" +
                        "        }\n" +
                        "    }\n" +
                        "}\n";
        try {
            SearchBuilder sb = SearchBuilder.createFromString(def);
            fail("Controlling dictionary for non-numeric fields are not yet supported.");
        } catch (IllegalArgumentException e) {
            assertEquals("For search 'test', field 'n1': You can only specify 'dictionary:' for numeric fields", e.getMessage());
        }
    }
    @Test
    public void testNonFastSearchFieldsFailsDictionaryControl() throws ParseException {
        String def =
                "search test {\n" +
                        "    document test {\n" +
                        "        field n1 type int {\n" +
                        "            indexing: summary | attribute\n" +
                        "            dictionary:btree\n" +
                        "        }\n" +
                        "    }\n" +
                        "}\n";
        try {
            SearchBuilder sb = SearchBuilder.createFromString(def);
            fail("Controlling dictionary for non-fast-search fields are not allowed.");
        } catch (IllegalArgumentException e) {
            assertEquals("For search 'test', field 'n1': You must specify 'attribute:fast-search' to allow dictionary control", e.getMessage());
        }
    }
}
