// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.vespa.documentmodel.SummaryElementsSelector;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.documentmodel.SummaryTransform;
import com.yahoo.vespa.model.test.utils.DeployLoggerStub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Geir Storli
 */
public class SummaryElementsSelectorValidatorTestCase {

    private DeployLoggerStub deployLogger = new DeployLoggerStub();

    @BeforeEach
    public void setup() {
        deployLogger = new DeployLoggerStub();
    }

    @Test
    void complex_field_with_some_struct_field_attributes_gets_default_transform() throws ParseException {
        assertSummaryField(joinLines("field my_field type map<string, string> {",
                "  indexing: summary",
                "  summary: matched-elements-only",
                "  struct-field key { indexing: attribute }",
                "}"),
            "my_field", SummaryTransform.NONE,
            SummaryElementsSelector.Select.BY_MATCH, "");

        assertSummaryField(joinLines("field my_field type map<string, elem> {",
                "  indexing: summary",
                "  summary: matched-elements-only",
                "  struct-field key { indexing: attribute }",
                "}"),
            "my_field", SummaryTransform.NONE,
            SummaryElementsSelector.Select.BY_MATCH, "");

        assertSummaryField(joinLines("field my_field type array<elem> {",
                "  indexing: summary",
                "  summary: matched-elements-only",
                "  struct-field name { indexing: attribute }",
                "}"),
            "my_field", SummaryTransform.NONE,
            SummaryElementsSelector.Select.BY_MATCH, "");
    }

    @Test
    void complex_field_with_only_struct_field_attributes_gets_attribute_transform() throws ParseException {
        assertSummaryField(joinLines("field my_field type map<string, string> {",
                "  indexing: summary",
                "  summary: matched-elements-only",
                "  struct-field key { indexing: attribute }",
                "  struct-field value { indexing: attribute }",
                "}"),
            "my_field", SummaryTransform.ATTRIBUTECOMBINER,
            SummaryElementsSelector.Select.BY_MATCH, "");

        assertSummaryField(joinLines("field my_field type map<string, elem> {",
                "  indexing: summary",
                "  summary: matched-elements-only",
                "  struct-field key { indexing: attribute }",
                "  struct-field value.name { indexing: attribute }",
                "  struct-field value.weight { indexing: attribute }",
                "}"),
            "my_field", SummaryTransform.ATTRIBUTECOMBINER,
            SummaryElementsSelector.Select.BY_MATCH, "");

        assertSummaryField(joinLines("field my_field type array<elem> {",
                "  indexing: summary",
                "  summary: matched-elements-only",
                "  struct-field name { indexing: attribute }",
                "  struct-field weight { indexing: attribute }",
                "}"),
            "my_field", SummaryTransform.ATTRIBUTECOMBINER,
            SummaryElementsSelector.Select.BY_MATCH, "");
    }

    @Test
    void explicit_complex_summary_field_can_use_filter_transform_with_reference_to_source_field() throws ParseException {
        String documentSummary = joinLines("document-summary my_summary {",
                "  summary my_filter_field {",
                "    source: my_field",
                "    matched-elements-only",
                "  }",
                "}");
        {
            var search = buildSearch(joinLines("field my_field type map<string, string> {",
                    "  indexing: summary",
                    "  struct-field key { indexing: attribute }",
                    "}"),
                    documentSummary);
            assertSummaryField(search.getSummaryField("my_filter_field"),
                SummaryTransform.COPY, "my_field",
                SummaryElementsSelector.Select.BY_MATCH, "");
            assertSummaryField(search.getSummaryField("my_field"),
                SummaryTransform.NONE, "my_field",
                SummaryElementsSelector.Select.ALL, "");
        }
        {
            var search = buildSearch(joinLines("field my_field type map<string, string> {",
                    "  indexing: summary",
                    "  struct-field key { indexing: attribute }",
                    "  struct-field value { indexing: attribute }",
                    "}"),
                    documentSummary);
            assertSummaryField(search.getSummaryField("my_filter_field"),
                SummaryTransform.ATTRIBUTECOMBINER, "my_field",
                SummaryElementsSelector.Select.BY_MATCH, "");
            assertSummaryField(search.getSummaryField("my_field"),
                SummaryTransform.ATTRIBUTECOMBINER, "my_field",
                SummaryElementsSelector.Select.ALL, "");
        }
    }

    @Test
    void primitive_array_attribute_field_gets_attribute_transform() throws ParseException {
        assertSummaryField(joinLines("field my_field type array<string> {",
                "  indexing: attribute | summary",
                "  summary: matched-elements-only",
                "}"),
                "my_field", SummaryTransform.ATTRIBUTE,
            SummaryElementsSelector.Select.BY_MATCH, "");
    }

    @Test
    void primitive_weighted_set_attribute_field_gets_attribute_transform() throws ParseException {
        assertSummaryField(joinLines("field my_field type weightedset<string> {",
                "  indexing: attribute | summary",
                "  summary: matched-elements-only",
                "}"),
                "my_field", SummaryTransform.ATTRIBUTE,
            SummaryElementsSelector.Select.BY_MATCH, "");
    }

    @Test
    void explicit_summary_field_can_use_filter_transform_with_reference_to_attribute_source_field() throws ParseException {
        String documentSummary = joinLines("document-summary my_summary {",
                "  summary my_filter_field {",
                "    source: my_field",
                "    matched-elements-only",
                "  }",
                "}");

        var search = buildSearch(joinLines(
                "field my_field type array<string> {",
                "  indexing: attribute | summary",
                "}"),
                documentSummary);
        assertSummaryField(search.getSummaryField("my_filter_field"),
            SummaryTransform.ATTRIBUTE, "my_field",
            SummaryElementsSelector.Select.BY_MATCH, "");
        assertSummaryField(search.getSummaryField("my_field"),
            SummaryTransform.ATTRIBUTE, "my_field",
            SummaryElementsSelector.Select.ALL, "");
    }

    @Test
    void unsupported_matched_elements_only_field_type_throws() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            buildSearch(joinLines("field my_field type string {",
                    "  indexing: summary",
                    "  summary: matched-elements-only",
                    "}"));
        });
        assertTrue(exception.getMessage().contains("For schema 'test', document-summary 'default', summary field 'my_field': " +
                "'matched-elements-only' is not supported for this field type. " +
                "Supported field types are: array of primitive, weighted set of primitive, " +
                "array of simple struct, map of primitive type to simple struct, " +
                "and map of primitive type to primitive type"));
    }

    @Test
    void unsupported_select_elements_by_field_type_throws() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            buildSearch(joinLines("field my_field type string {",
                    "  indexing: summary",
                    "}"),
                """
                    document-summary default {
                       summary my_field {
                          select-elements-by: elementwise(bm25(my_field),x,double)
                       }
                    }
                    """);
        });
        assertTrue(exception.getMessage().contains("For schema 'test', document-summary 'default', summary field 'my_field': " +
            "'select-elements-by' is not supported for this field type. " +
            "Supported field types are: array of primitive, weighted set of primitive, " +
            "array of simple struct, map of primitive type to simple struct, " +
            "and map of primitive type to primitive type"));
    }

    @Test
    void select_elements_by_referencing_non_existing_summary_feature_logs() throws ParseException {
        // Should fail, as 'dist_scores' is not defined as a summary feature
        buildSearch("""
                      field text type array<string> {
                        indexing: summary
                      }
                      field pos type tensor<float>(chunk{},xy[2]) {
                        indexing: attribute
                      }
                      """,
                      """
                      document-summary default {
                        summary text {
                          select-elements-by: dist_scores
                        }
                      }
                      rank-profile default {
                        inputs {
                          query(qpos) tensor<float>(xy[2])
                        }
                        function dist_scores() {
                          expression: 1/(1+euclidean_distance(query(qpos), attribute(pos), xy))
                        }
                      }
                      """);
        var message = deployLogger.getLast().message;
        assertTrue(message.contains("For schema 'test', document-summary 'default', summary field 'text': " +
                                    "select-elements-by summary feature 'dist_scores' is not defined for source field 'text'."),
                   message);


        // Should work (no logging), as 'dist_scores' is defined as a summary feature now
        deployLogger.entries.clear();
        buildSearch("""
                      field text type array<string> {
                        indexing: summary
                      }
                      field pos type tensor<float>(chunk{},xy[2]) {
                        indexing: attribute
                      }
                    """,
                    """
                    document-summary default {
                      summary text {
                        select-elements-by: dist_scores
                      }
                    }
                    rank-profile default {
                      inputs {
                        query(qpos) tensor<float>(xy[2])
                      }
                      function dist_scores() {
                        expression: 1/(1+euclidean_distance(query(qpos), attribute(pos), xy))
                      }
                      summary-features {
                        dist_scores
                      }
                    }
                    """);
        assertEquals(0, deployLogger.entries.size());
    }

    private void assertSummaryField(String fieldContent, String fieldName, SummaryTransform expTransform,
                                    SummaryElementsSelector.Select expSelect,
                                    String expSummaryFeature) throws ParseException {
        var search = buildSearch(fieldContent);
        assertSummaryField(search.getSummaryField(fieldName), expTransform, fieldName, expSelect, expSummaryFeature);
    }

    private void assertSummaryField(SummaryField field, SummaryTransform expTransform, String expSourceField,
                                    SummaryElementsSelector.Select expSelect, String expSummaryFeature) {
        assertEquals(expTransform, field.getTransform());
        assertEquals(expSourceField, field.getSingleSource());
        assertEquals(expSelect, field.getElementsSelector().getSelect());
        assertEquals(expSummaryFeature, field.getElementsSelector().getSummaryFeature());
    }

    private Schema buildSearch(String field) throws ParseException {
        return buildSearch(field, "");
    }

    private Schema buildSearch(String field, String summary) throws ParseException {
        var builder = new ApplicationBuilder(deployLogger, new RankProfileRegistry());
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
