// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.searchdefinition.processing;

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
    @Test
    public void testDefaultDictionarySettings() throws ParseException {
        String def =
                "search test {\n" +
                "    document test {\n" +
                "        field s1 type string {\n" +
                "            indexing: attribute | summary\n" +
                "        }\n" +
                "\n" +
                "        field n1 type int {\n" +
                "            indexing: summary | attribute\n" +
                "        }\n" +
                "    }\n" +
                "}\n";
        SearchBuilder sb = SearchBuilder.createFromString(def);
        Search search = sb.getSearch();
        assertEquals(Dictionary.Type.BTREE, search.getAttribute("s1").getDictionary().getType());
        assertEquals(Dictionary.Type.BTREE, search.getAttribute("n1").getDictionary().getType());
    }
    @Test
    public void testNumericBtreeSettings() throws ParseException {
        String def =
                "search test {\n" +
                "    document test {\n" +
                "        field n1 type int {\n" +
                "            indexing: summary | attribute\n" +
                "            attribute:fast-search\n" +
                "            dictionary:btree\n" +
                "        }\n" +
                "    }\n" +
                "}\n";
        SearchBuilder sb = SearchBuilder.createFromString(def);
        Search search = sb.getSearch();
        assertEquals(Dictionary.Type.BTREE, search.getAttribute("n1").getDictionary().getType());
        assertEquals(AttributesConfig.Attribute.Dictionary.Type.BTREE,
                getConfig(search).attribute().get(0).dictionary().type());
    }
    @Test
    public void testNumericHashSettings() throws ParseException {
        String def =
                "search test {\n" +
                "    document test {\n" +
                "        field n1 type int {\n" +
                "            indexing: summary | attribute\n" +
                "            attribute:fast-search\n" +
                "            dictionary:hash\n" +
                "        }\n" +
                "    }\n" +
                "}\n";
        SearchBuilder sb = SearchBuilder.createFromString(def);
        Search search = sb.getSearch();
        assertEquals(Dictionary.Type.HASH, search.getAttribute("n1").getDictionary().getType());
        assertEquals(AttributesConfig.Attribute.Dictionary.Type.HASH,
                getConfig(search).attribute().get(0).dictionary().type());
    }
    @Test
    public void testNumericBtreeAndHashSettings() throws ParseException {
        String def =
                "search test {\n" +
                        "    document test {\n" +
                        "        field n1 type int {\n" +
                        "            indexing: summary | attribute\n" +
                        "            attribute:fast-search\n" +
                        "            dictionary:hash\n" +
                        "            dictionary:btree\n" +
                        "        }\n" +
                        "    }\n" +
                        "}\n";
        SearchBuilder sb = SearchBuilder.createFromString(def);
        Search search = sb.getSearch();
        assertEquals(Dictionary.Type.BTREE_AND_HASH, search.getAttribute("n1").getDictionary().getType());
        assertEquals(AttributesConfig.Attribute.Dictionary.Type.BTREE_AND_HASH,
                     getConfig(search).attribute().get(0).dictionary().type());
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
            assertEquals("For search 'test', field 'n1': You must specify attribute:fast-search to allow dictionary control", e.getMessage());
        }
    }
}
