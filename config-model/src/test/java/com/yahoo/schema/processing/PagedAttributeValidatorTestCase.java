// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static com.yahoo.schema.ApplicationBuilder.createFromString;
import static com.yahoo.schema.ApplicationBuilder.createFromStrings;
import static org.junit.jupiter.api.Assertions.*;

public class PagedAttributeValidatorTestCase {

    @Test
    void dense_tensor_attribute_supports_paged_setting() throws ParseException {
        assertPagedSupported("tensor(x[2],y[2])");
    }

    @Test
    void primitive_attribute_types_support_paged_setting() throws ParseException {
        assertPagedSupported("int");
        assertPagedSupported("array<int>");
        assertPagedSupported("weightedset<int>");

        assertPagedSupported("string");
        assertPagedSupported("array<string>");
        assertPagedSupported("weightedset<string>");
    }

    @Test
    void struct_field_attributes_support_paged_setting() throws ParseException {
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
        assertPagedSupported(fieldType, Optional.empty());
    }

    private void assertPagedSupported(String fieldType, Optional<String> parentSd) throws ParseException {
        var appBuilder = parentSd.isPresent() ?
                createFromStrings(new BaseDeployLogger(), parentSd.get(), getSd(fieldType)) :
                createFromString(getSd(fieldType));
        var attribute = appBuilder.getSchema("test").getAttribute("foo");
        assertTrue(attribute.isPaged());
    }

    @Test
    void non_dense_none_fast_rank_tensor_attribute_supports_paged_setting() throws ParseException {
        assertPagedSupported("tensor(x{},y[2])");
    }

    @Test
    void non_dense_fast_rank_tensor_attribute_does_not_support_paged_setting() throws ParseException {
        assertPagedSettingNotSupported("tensor(x{},y[2])", true);
    }

    @Test
    void predicate_attribute_does_not_support_paged_setting() throws ParseException {
        assertPagedSettingNotSupported("predicate");
    }

    @Test
    void reference_attribute_support_paged_setting() throws ParseException {
        assertPagedSupported("reference<parent>", Optional.of(getSd("parent", "int", false)));
    }

    private void assertPagedSettingNotSupported(String fieldType) throws ParseException {
        assertPagedSettingNotSupported(fieldType, false);
    }

    private void assertPagedSettingNotSupported(String fieldType, boolean fastRank) throws ParseException {
        assertPagedSettingNotSupported(fieldType, fastRank, Optional.empty());
    }

    private void assertPagedSettingNotSupported(String fieldType, boolean fastRank, Optional<String> parentSd) throws ParseException {
        try {
            if (parentSd.isPresent()) {
                createFromStrings(new BaseDeployLogger(), parentSd.get(), getSd(fieldType, fastRank));
            } else {
                createFromString(getSd(fieldType, fastRank));
            }
            fail("Expected exception");
        }  catch (IllegalArgumentException e) {
            assertEquals("For schema 'test', field 'foo': The 'paged' attribute setting is not supported for fast-rank tensor and predicate types",
                    e.getMessage());
        }
    }

    private String getSd(String fieldType) {
        return getSd(fieldType, false);
    }

    private String getSd(String fieldType, boolean fastRank) {
        return getSd("test", fieldType, fastRank);
    }

    private String getSd(String docType, String fieldType, boolean fastRank) {
        return joinLines(
                "schema " + docType + " {",
                "  document " + docType + " {",
                "    field foo type " + fieldType + "{",
                (fastRank ? "attribute: fast-rank" : ""),
                "      indexing: attribute",
                "      attribute: paged",
                "    }",
                "  }",
                "}");
    }

}
