// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.model.test.TestUtil;
import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;


import static com.yahoo.searchdefinition.SearchBuilder.createFromString;
import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author geirst
 */
public class TensorFieldTestCase {

    @Test
    public void requireThatTensorFieldCannotBeOfCollectionType() throws ParseException {
        try {
            createFromString(getSd("field f1 type array<tensor(x{})> {}"));
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("For search 'test', field 'f1': A field with collection type of tensor is not supported. Use simple type 'tensor' instead.",
                         e.getMessage());
        }
    }

    @Test
    public void requireThatTensorFieldCannotBeIndexField() throws ParseException {
        try {
            createFromString(getSd("field f1 type tensor(x{}) { indexing: index }"));
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("For search 'test', field 'f1': A tensor of type 'tensor(x{})' does not support having an 'index'. " +
                            "Currently, only tensors with 1 indexed dimension supports that.",
                         e.getMessage());
        }
    }

    @Test
    public void requireThatIndexedTensorAttributeCannotBeFastSearch() throws ParseException {
        try {
            createFromString(getSd("field f1 type tensor(x[3]) { indexing: attribute \n attribute: fast-search }"));
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("For search 'test', field 'f1': An attribute of type 'tensor' cannot be 'fast-search'.", e.getMessage());
        }
    }

    @Test
    public void requireThatIllegalTensorTypeSpecThrowsException() throws ParseException {
        try {
            createFromString(getSd("field f1 type tensor(invalid) { indexing: attribute }"));
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertStartsWith("Field type: Illegal tensor type spec:", e.getMessage());
        }
    }

    @Test
    public void hnsw_index_is_default_turned_off() throws ParseException {
        var attr = getAttributeFromSd("field t1 type tensor(x[64]) { indexing: attribute }", "t1");
        assertFalse(attr.hnswIndexParams().isPresent());
    }

    @Test
    public void hnsw_index_gets_default_parameters_if_not_specified() throws ParseException {
        assertHnswIndexParams("", 16, 200);
        assertHnswIndexParams("index: hnsw", 16, 200);
    }

    @Test
    public void hnsw_index_parameters_can_be_specified() throws ParseException {
        assertHnswIndexParams("index { hnsw { max-links-per-node: 32 } }", 32, 200);
        assertHnswIndexParams("index { hnsw { neighbors-to-explore-at-insert: 300 } }", 16, 300);
        assertHnswIndexParams(joinLines("index {",
                "  hnsw {",
                "    max-links-per-node: 32",
                "    neighbors-to-explore-at-insert: 300",
                "  }",
                "}"),
                32, 300);
    }

    @Test
    public void tensor_with_hnsw_index_must_be_an_attribute() throws ParseException {
        try {
            createFromString(getSd("field t1 type tensor(x[64]) { indexing: index }"));
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("For search 'test', field 't1': A tensor that has an index must also be an attribute.", e.getMessage());
        }
    }

    @Test
    public void tensors_with_at_least_one_mapped_dimension_can_be_direct() throws ParseException {
        assertTrue(getAttributeFromSd(
                "field t1 type tensor(x{}) { indexing: attribute \n attribute: fast-search }", "t1").isFastSearch());
        assertTrue(getAttributeFromSd(
                "field t1 type tensor(x{},y{},z[4]) { indexing: attribute \n attribute: fast-search }", "t1").isFastSearch());
    }

    private static String getSd(String field) {
        return joinLines("search test {",
                "  document test {",
                "    " + field,
                "  }",
                "}");
    }

    private Attribute getAttributeFromSd(String fieldSpec, String attrName) throws ParseException {
        return createFromString(getSd(fieldSpec)).getSearch().getAttribute(attrName);
    }

    private void assertHnswIndexParams(String indexSpec, int maxLinksPerNode, int neighborsToExploreAtInsert) throws ParseException {
        var sd = getSdWithIndexSpec(indexSpec);
        System.out.println(sd);
        var search = createFromString(sd).getSearch();
        var attr = search.getAttribute("t1");
        var params = attr.hnswIndexParams();
        assertTrue(params.isPresent());
        assertEquals(maxLinksPerNode, params.get().maxLinksPerNode());
        assertEquals(neighborsToExploreAtInsert, params.get().neighborsToExploreAtInsert());
    }

    private String getSdWithIndexSpec(String indexSpec) {
        return getSd(joinLines("field t1 type tensor(x[64]) {",
                "  indexing: attribute | index",
                "  " + indexSpec,
                "}"));
    }

    private void assertStartsWith(String prefix, String string) {
        assertEquals(prefix, string.substring(0, Math.min(prefix.length(), string.length())));
    }

}
