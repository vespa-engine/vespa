// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.SearchDefinitionTestCase;
import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.io.IOException;
import java.util.Iterator;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests attribute deriving
 *
 * @author bratseth
 */
public class AttributeListTestCase extends SearchDefinitionTestCase {

    @Test
    public void testDeriving() throws IOException, ParseException {
        // Test attribute importing
        Search search = SearchBuilder.buildFromFile("src/test/examples/simple.sd");

        // Test attribute deriving
        AttributeFields attributeFields = new AttributeFields(search);
        Iterator attributes = attributeFields.attributeIterator();
        Attribute attribute;
        attribute = (Attribute)attributes.next();
        assertEquals("popularity", attribute.getName());
        assertEquals(Attribute.Type.INTEGER, attribute.getType());
        assertEquals(Attribute.CollectionType.SINGLE, attribute.getCollectionType());

        attribute = (Attribute)attributes.next();
        assertEquals("measurement", attribute.getName());
        assertEquals(Attribute.Type.INTEGER, attribute.getType());
        assertEquals(Attribute.CollectionType.SINGLE, attribute.getCollectionType());

        attribute = (Attribute)attributes.next();
        assertEquals("smallattribute", attribute.getName());
        assertEquals(Attribute.Type.BYTE, attribute.getType());
        assertEquals(Attribute.CollectionType.ARRAY, attribute.getCollectionType());

        attribute = (Attribute)attributes.next();
        assertEquals("access", attribute.getName());
        assertEquals(Attribute.Type.BYTE, attribute.getType());
        assertEquals(Attribute.CollectionType.SINGLE, attribute.getCollectionType());

        attribute = (Attribute)attributes.next();
        assertEquals("category_arr", attribute.getName());
        assertEquals(Attribute.Type.STRING, attribute.getType());
        assertEquals(Attribute.CollectionType.ARRAY, attribute.getCollectionType());

        attribute = (Attribute)attributes.next();
        assertEquals("measurement_arr", attribute.getName());
        assertEquals(Attribute.Type.INTEGER, attribute.getType());
        assertEquals(Attribute.CollectionType.ARRAY, attribute.getCollectionType());

        attribute = (Attribute)attributes.next();
        assertEquals("popsiness", attribute.getName());
        assertEquals(Attribute.Type.INTEGER, attribute.getType());
        assertEquals(Attribute.CollectionType.SINGLE, attribute.getCollectionType());

        assertTrue(!attributes.hasNext());
    }

    @Test
    public void fields_in_array_of_struct_are_derived_into_array_attributes() throws IOException, ParseException {
        Search search = SearchBuilder.buildFromFile("src/test/derived/array_of_struct_attribute/test.sd");
        Iterator<Attribute> attributes = new AttributeFields(search).attributeIterator();

        assertAttribute("elem_array.name", Attribute.Type.STRING, Attribute.CollectionType.ARRAY, true, attributes.next());
        assertAttribute("elem_array.weight", Attribute.Type.INTEGER, Attribute.CollectionType.ARRAY, false, attributes.next());
        assertTrue(!attributes.hasNext());
    }

    @Test
    public void fields_in_map_of_struct_are_derived_into_array_attributes() throws IOException, ParseException {
        Search search = SearchBuilder.buildFromFile("src/test/derived/map_of_struct_attribute/test.sd");
        Iterator<Attribute> attributes = new AttributeFields(search).attributeIterator();

        assertAttribute("str_elem_map.key", Attribute.Type.STRING, Attribute.CollectionType.ARRAY, true, attributes.next());
        assertAttribute("str_elem_map.value.name", Attribute.Type.STRING, Attribute.CollectionType.ARRAY, false, attributes.next());
        assertAttribute("str_elem_map.value.weight", Attribute.Type.INTEGER, Attribute.CollectionType.ARRAY, false, attributes.next());
        assertAttribute("int_elem_map.key", Attribute.Type.INTEGER, Attribute.CollectionType.ARRAY, false, attributes.next());
        assertAttribute("int_elem_map.value.name", Attribute.Type.STRING, Attribute.CollectionType.ARRAY, true, attributes.next());
        assertTrue(!attributes.hasNext());
    }

    private static void assertAttribute(String name, Attribute.Type type, Attribute.CollectionType collection, boolean isFastSearch, Attribute attr) {
        assertEquals(name, attr.getName());
        assertEquals(type, attr.getType());
        assertEquals(collection, attr.getCollectionType());
        assertEquals(isFastSearch, attr.isFastSearch());
    }

    @Test
    public void only_zcurve_attribute_is_derived_from_array_of_position_field() throws ParseException {
        Search search = SearchBuilder.createFromString(
                joinLines("search test {",
                          "  document test {",
                          "    field pos_array type array<position> {",
                          "      indexing: attribute",
                          "    }",
                          "  }",
                          "}")).getSearch();
        Iterator<Attribute> attributes = new AttributeFields(search).attributeIterator();

        assertAttribute("pos_array_zcurve", Attribute.Type.LONG, Attribute.CollectionType.ARRAY, true, attributes.next());
        assertTrue(!attributes.hasNext());
    }

    @Test
    public void fields_in_map_of_primitive_are_derived_into_array_attributes() throws IOException, ParseException {
        Search search = SearchBuilder.buildFromFile("src/test/derived/map_attribute/test.sd");
        Iterator<Attribute> attributes = new AttributeFields(search).attributeIterator();

        assertAttribute("str_map.key", Attribute.Type.STRING, Attribute.CollectionType.ARRAY, true, attributes.next());
        assertAttribute("str_map.value", Attribute.Type.STRING, Attribute.CollectionType.ARRAY, false, attributes.next());
        assertAttribute("int_map.key", Attribute.Type.INTEGER, Attribute.CollectionType.ARRAY, false, attributes.next());
        assertTrue(!attributes.hasNext());
    }

}
