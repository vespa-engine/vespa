// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author geirst
 */
public class TensorFieldTestCase {

    @Test
    public void requireThatTensorFieldCannotBeOfCollectionType() throws ParseException {
        try {
            SearchBuilder.createFromString(getSd("field f1 type array<tensor(x{})> {}"));
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
            SearchBuilder.createFromString(getSd("field f1 type tensor(x{}) { indexing: index }"));
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("For search 'test', field 'f1': A field of type 'tensor' cannot be specified as an 'index' field.",
                         e.getMessage());
        }
    }

    @Test
    public void requireThatTensorAttributeCannotBeFastSearch() throws ParseException {
        try {
            SearchBuilder.createFromString(getSd("field f1 type tensor(x{}) { indexing: attribute \n attribute: fast-search }"));
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("For search 'test', field 'f1': An attribute of type 'tensor' cannot be 'fast-search'.", e.getMessage());
        }
    }

    @Test
    public void requireThatIllegalTensorTypeSpecThrowsException() throws ParseException {
        try {
            SearchBuilder.createFromString(getSd("field f1 type tensor(invalid) { indexing: attribute }"));
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertStartsWith("Field type: Illegal tensor type spec:", e.getMessage());
        }
    }

    private static String getSd(String field) {
        return "search test {\n document test {\n" + field + "}\n}\n";
    }

    private void assertStartsWith(String prefix, String string) {
        assertEquals(prefix, string.substring(0, Math.min(prefix.length(), string.length())));
    }

}
