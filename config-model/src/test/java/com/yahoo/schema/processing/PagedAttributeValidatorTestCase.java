// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.schema.parser.ParseException;
import org.junit.Test;

import java.util.Optional;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static com.yahoo.schema.ApplicationBuilder.createFromString;
import static com.yahoo.schema.ApplicationBuilder.createFromStrings;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PagedAttributeValidatorTestCase {

    @Test
    public void dense_tensor_attribute_supports_paged_setting() throws ParseException {
        assertPagedSupported("tensor(x[2],y[2])");
    }

    @Test
    public void primitive_attribute_types_support_paged_setting() throws ParseException {
        assertPagedSupported("int");
        assertPagedSupported("array<int>");
        assertPagedSupported("weightedset<int>");

        assertPagedSupported("string");
        assertPagedSupported("array<string>");
        assertPagedSupported("weightedset<string>");
    }

    @Test
    public void struct_field_attributes_support_paged_setting() throws ParseException {
        var sd = joinLines("schema test {",
                "  document test {",
                "    struct elem {",
                "      field first type int {}",
                "      field second type string {}",
                "    }",
                "    field foo type array<elem> {",
                "      indexing: summary",
                "      struct-field first {",
                "        indexing: attribute",
                "        attribute: paged",
                "      }",
                "      struct-field second {",
                "        indexing: attribute",
                "        attribute: paged",
                "      }",
                "    }",
                "  }",
                "}");

        var appBuilder = createFromString(sd);
        var field = appBuilder.getSchema().getField("foo");
        assertTrue(field.getStructField("first").getAttribute().isPaged());
        assertTrue(field.getStructField("second").getAttribute().isPaged());
    }

    private void assertPagedSupported(String fieldType) throws ParseException {
        var appBuilder = createFromString(getSd(fieldType));
        var attribute = appBuilder.getSchema().getAttribute("foo");
        assertTrue(attribute.isPaged());
    }

    @Test
    public void non_dense_tensor_attribute_does_not_support_paged_setting() throws ParseException {
        assertPagedSettingNotSupported("tensor(x{},y[2])");
    }

    @Test
    public void predicate_attribute_does_not_support_paged_setting() throws ParseException {
        assertPagedSettingNotSupported("predicate");
    }

    @Test
    public void reference_attribute_does_not_support_paged_setting() throws ParseException {
        assertPagedSettingNotSupported("reference<parent>", Optional.of(getSd("parent", "int")));
    }

    private void assertPagedSettingNotSupported(String fieldType) throws ParseException {
        assertPagedSettingNotSupported(fieldType, Optional.empty());
    }

    private void assertPagedSettingNotSupported(String fieldType, Optional<String> parentSd) throws ParseException {
        try {
            if (parentSd.isPresent()) {
                createFromStrings(new BaseDeployLogger(), parentSd.get(), getSd(fieldType));
            } else {
                createFromString(getSd(fieldType));
            }
            fail("Expected exception");
        }  catch (IllegalArgumentException e) {
            assertEquals("For schema 'test', field 'foo': The 'paged' attribute setting is not supported for non-dense tensor, predicate and reference types",
                    e.getMessage());
        }
    }

    private String getSd(String fieldType) {
        return getSd("test", fieldType);
    }

    private String getSd(String docType, String fieldType) {
        return joinLines(
                "schema " + docType + " {",
                "  document " + docType + " {",
                "    field foo type " + fieldType + "{",
                "      indexing: attribute",
                "      attribute: paged",
                "    }",
                "  }",
                "}");
    }

}
