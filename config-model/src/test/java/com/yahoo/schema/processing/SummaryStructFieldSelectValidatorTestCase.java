// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.vespa.documentmodel.SummaryElementsSelector;
import com.yahoo.vespa.documentmodel.SummaryTransform;
import com.yahoo.vespa.model.test.utils.DeployLoggerStub;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author arnej
 */
public class SummaryStructFieldSelectValidatorTestCase {

    private static final String UNSUPPORTED_TYPE_MESSAGE =
            "'struct-field' is not supported for this field type. " +
            "Supported field types are: array of struct, map of primitive type to struct, " +
            "and map of primitive type to primitive type";

    @Test
    void struct_field_select_is_allowed_for_array_of_struct() throws ParseException {
        var schema = buildSchema(joinLines("field my_field type array<elem> {",
                                           "  indexing: summary",
                                           "  struct-field name { indexing: attribute }",
                                           "  struct-field weight { indexing: attribute }",
                                           "}"),
                                 joinLines("document-summary my_summary {",
                                           "  summary my_field {",
                                           "    struct-field: name",
                                           "  }",
                                           "}"));
        var summaryField = schema.getSummary("my_summary").getSummaryField("my_field");
        assertEquals(SummaryTransform.ATTRIBUTECOMBINER, summaryField.getTransform());
        assertEquals(List.of("name"), summaryField.getStructFields());
    }

    @Test
    void struct_field_select_is_allowed_for_map() throws ParseException {
        var schema = buildSchema(joinLines("field my_field type map<string, int> {",
                                           "  indexing: summary",
                                           "  struct-field key { indexing: attribute }",
                                           "  struct-field value { indexing: attribute }",
                                           "}"),
                                 joinLines("document-summary my_summary {",
                                           "  summary my_field {",
                                           "    struct-field: key",
                                           "  }",
                                           "}"));
        var summaryField = schema.getSummary("my_summary").getSummaryField("my_field");
        assertEquals(SummaryTransform.ATTRIBUTECOMBINER, summaryField.getTransform());
        assertEquals(List.of("key"), summaryField.getStructFields());
    }

    @Test
    void struct_field_select_is_allowed_for_map_of_struct() throws ParseException {
        var schema = buildSchema(joinLines("field my_field type map<string, elem> {",
                                           "  indexing: summary",
                                           "  struct-field key { indexing: attribute }",
                                           "  struct-field value.name { indexing: attribute }",
                                           "  struct-field value.weight { indexing: attribute }",
                                           "}"),
                                 joinLines("document-summary my_summary {",
                                           "  summary my_field {",
                                           "    struct-field: value.name",
                                           "  }",
                                           "}"));
        var summaryField = schema.getSummary("my_summary").getSummaryField("my_field");
        assertEquals(SummaryTransform.ATTRIBUTECOMBINER, summaryField.getTransform());
        assertEquals(List.of("value.name"), summaryField.getStructFields());
    }

    @Test
    void struct_field_select_for_map_of_struct_must_name_a_field_of_the_value_struct() {
        var exception = assertThrows(IllegalArgumentException.class, () ->
                buildSchema(joinLines("field my_field type map<string, elem> {",
                                      "  indexing: summary",
                                      "  struct-field key { indexing: attribute }",
                                      "  struct-field value.name { indexing: attribute }",
                                      "  struct-field value.weight { indexing: attribute }",
                                      "}"),
                            joinLines("document-summary my_summary {",
                                      "  summary my_field {",
                                      "    struct-field: value",
                                      "  }",
                                      "}")));
        assertTrue(exception.getMessage().contains("For schema 'test', document-summary 'my_summary', " +
                                                   "summary field 'my_field': struct-field 'value' is not a field " +
                                                   "of 'my_field', expected one of [key, value.name, value.weight]"),
                   exception.getMessage());
    }

    @Test
    void struct_field_select_can_exclude_a_value_field_which_is_not_usable_as_an_attribute() throws ParseException {
        var schema = buildSchema(joinLines("field my_field type map<string, elem> {",
                                           "  indexing: summary",
                                           "  struct-field key { indexing: attribute }",
                                           "  struct-field value.name { indexing: attribute }",
                                           "}"),
                                 joinLines("document-summary my_summary {",
                                           "  summary my_field {",
                                           "    struct-field: value.name",
                                           "  }",
                                           "}"));
        var summaryField = schema.getSummary("my_summary").getSummaryField("my_field");
        assertEquals(SummaryTransform.ATTRIBUTECOMBINER, summaryField.getTransform());
    }

    @Test
    void duplicate_struct_field_selections_are_ignored() throws ParseException {
        var schema = buildSchema(joinLines("field my_field type array<elem> {",
                                           "  indexing: summary",
                                           "  struct-field name { indexing: attribute }",
                                           "  struct-field weight { indexing: attribute }",
                                           "}"),
                                 joinLines("document-summary my_summary {",
                                           "  summary my_field {",
                                           "    struct-field: name",
                                           "    struct-field: name",
                                           "  }",
                                           "}"));
        var summaryField = schema.getSummary("my_summary").getSummaryField("my_field");
        assertEquals(List.of("name"), summaryField.getStructFields());
    }

    @Test
    void struct_field_select_can_be_combined_with_matched_elements_only() throws ParseException {
        var schema = buildSchema(joinLines("field my_field type array<elem> {",
                                           "  indexing: summary",
                                           "  struct-field name { indexing: attribute }",
                                           "  struct-field weight { indexing: attribute }",
                                           "}"),
                                 joinLines("document-summary my_summary {",
                                           "  summary my_filtered_field {",
                                           "    source: my_field",
                                           "    matched-elements-only",
                                           "    struct-field: name",
                                           "  }",
                                           "}"));
        var summaryField = schema.getSummary("my_summary").getSummaryField("my_filtered_field");
        assertEquals(SummaryTransform.ATTRIBUTECOMBINER, summaryField.getTransform());
        assertEquals(SummaryElementsSelector.Select.BY_MATCH, summaryField.getElementsSelector().getSelect());
        assertEquals(List.of("name"), summaryField.getStructFields());
    }

    @Test
    void struct_field_select_is_not_allowed_for_primitive_field() {
        var exception = assertThrows(IllegalArgumentException.class, () ->
                buildSchema(joinLines("field my_field type string {",
                                      "  indexing: summary | attribute",
                                      "}"),
                            joinLines("document-summary my_summary {",
                                      "  summary my_field {",
                                      "    struct-field: name",
                                      "  }",
                                      "}")));
        assertTrue(exception.getMessage().contains("For schema 'test', document-summary 'my_summary', " +
                                                   "summary field 'my_field': " + UNSUPPORTED_TYPE_MESSAGE),
                   exception.getMessage());
    }

    @Test
    void struct_field_select_is_not_allowed_for_array_of_primitive() {
        var exception = assertThrows(IllegalArgumentException.class, () ->
                buildSchema(joinLines("field my_field type array<string> {",
                                      "  indexing: summary | attribute",
                                      "}"),
                            joinLines("document-summary my_summary {",
                                      "  summary my_field {",
                                      "    struct-field: name",
                                      "  }",
                                      "}")));
        assertTrue(exception.getMessage().contains("For schema 'test', document-summary 'my_summary', " +
                                                   "summary field 'my_field': " + UNSUPPORTED_TYPE_MESSAGE),
                   exception.getMessage());
    }

    @Test
    void struct_field_select_is_not_allowed_together_with_a_summary_transform() {
        assertTransformIsRejected("dynamic", "dynamicteaser");
        assertTransformIsRejected("tokens", "tokens");
        assertTransformIsRejected("bolding: on", "bolded");
    }

    private void assertTransformIsRejected(String transformItem, String expectedTransformName) {
        var exception = assertThrows(IllegalArgumentException.class, () ->
                buildSchema(joinLines("field my_field type array<elem> {",
                                      "  indexing: summary",
                                      "  struct-field name { indexing: attribute }",
                                      "  struct-field weight { indexing: attribute }",
                                      "}"),
                            joinLines("document-summary my_summary {",
                                      "  summary my_other_field {",
                                      "    source: my_field",
                                      "    " + transformItem,
                                      "    struct-field: name",
                                      "  }",
                                      "}")));
        assertTrue(exception.getMessage().contains("For schema 'test', document-summary 'my_summary', " +
                                                   "summary field 'my_other_field': 'struct-field' cannot be " +
                                                   "combined with the '" + expectedTransformName +
                                                   "' summary transform"),
                   exception.getMessage());
    }

    @Test
    void struct_field_select_must_name_a_struct_field_of_the_source() {
        var exception = assertThrows(IllegalArgumentException.class, () ->
                buildSchema(joinLines("field my_field type array<elem> {",
                                      "  indexing: summary",
                                      "  struct-field name { indexing: attribute }",
                                      "  struct-field weight { indexing: attribute }",
                                      "}"),
                            joinLines("document-summary my_summary {",
                                      "  summary my_field {",
                                      "    struct-field: nosuch",
                                      "  }",
                                      "}")));
        assertTrue(exception.getMessage().contains("For schema 'test', document-summary 'my_summary', " +
                                                   "summary field 'my_field': struct-field 'nosuch' is not a field " +
                                                   "of 'my_field', expected one of [name, weight]"),
                   exception.getMessage());
    }

    private Schema buildSchema(String field, String summary) throws ParseException {
        var builder = new ApplicationBuilder(new DeployLoggerStub(), new RankProfileRegistry());
        builder.addSchema(joinLines("search test {",
                                    "  document test {",
                                    "    struct elem {",
                                    "      field name type string {}",
                                    "      field weight type int {}",
                                    "    }",
                                    field,
                                    "  }",
                                    summary,
                                    "}"));
        builder.build(true);
        return builder.getSchema();
    }
}
