// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static com.yahoo.searchdefinition.SearchBuilder.createFromString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class PagedAttributeValidatorTestCase {

    @Test
    public void dense_tensor_attribute_does_support_paged_setting() throws ParseException {
        createFromString(getSd("tensor(x[2],y[2])"));
    }

    @Test
    public void non_dense_tensor_attribute_does_not_support_paged_setting() throws ParseException {
        assertPagedSettingNotSupported("tensor(x{},y[2])");
    }

    @Test
    public void non_tensor_attribute_does_not_support_paged_setting() throws ParseException {
        assertPagedSettingNotSupported("string");
    }

    private void assertPagedSettingNotSupported(String fieldType) throws ParseException {
        try {
            createFromString(getSd(fieldType));
            fail("Expected exception");
        }  catch (IllegalArgumentException e) {
            assertEquals("For search 'test', field 'foo': The 'paged' attribute setting is only supported for dense tensor types",
                    e.getMessage());
        }
    }

    private String getSd(String type) {
        return joinLines("search test {",
                "  document test {",
                "    field foo type " + type + "{",
                "      indexing: attribute",
                "      attribute: paged",
                "    }",
                "  }",
                "}");
    }

}
