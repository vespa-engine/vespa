// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.Schema;
import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author arnej
 */
public class StructFieldIndexingTestCase {

    private static final String SCHEMA = joinLines(
            "schema test {",
            "  document test {",
            "    struct s {",
            "      field a type string {}",
            "      field b type string {}",
            "      field c type string {}",
            "    }",
            "    field arr type array<s> {",
            "      indexing: summary",
            "      struct-field a { indexing: index }",
            "      struct-field b { indexing: index",
            "                       match: exact }",
            "    }",
            "    field map type map<string, s> {",
            "      struct-field key { indexing: index }",
            "      struct-field value.c { indexing: index }",
            "    }",
            "    field plain type array<s> {",
            "      indexing: summary",
            "    }",
            "    field str type string {",
            "      indexing: index",
            "    }",
            "  }",
            "}");

    @Test
    void struct_field_indexing_script_annotates_indexed_struct_fields() throws ParseException {
        var schema = build("allow_alone", false);
        assertScript("{ input arr | for_each_field \"a\" { tokenize normalize stem:\"BEST\" } | " +
                     "for_each_field \"b\" { exact } | passthrough arr; }",
                     schema, "arr");
        assertScript("{ input map | for_each_field \"key\" { tokenize normalize stem:\"BEST\" } | " +
                     "for_each_field \"value.c\" { tokenize normalize stem:\"BEST\" } | passthrough map; }",
                     schema, "map");
    }

    @Test
    void struct_field_indexing_script_is_empty_without_struct_field_indexes() throws ParseException {
        var schema = build("allow_alone", false);
        assertEmptyScript(schema, "plain");
        assertEmptyScript(schema, "str");
    }

    @Test
    void struct_field_indexing_script_is_empty_when_struct_field_indexes_are_not_allowed() throws ParseException {
        var schema = build("ignore", false);
        assertEmptyScript(schema, "arr");
        assertEmptyScript(schema, "map");
    }

    /** Without this processor no struct field is annotated, so it must not be skipped. */
    @Test
    void struct_field_indexing_script_is_empty_when_the_processor_is_skipped() throws ParseException {
        var schema = build("allow_alone", true);
        assertEmptyScript(schema, "arr");
        assertEmptyScript(schema, "map");
    }

    private static Schema build(String allowStructFieldIndex, boolean skipProcessor) throws ParseException {
        var builder = new ApplicationBuilder(new TestProperties().allowStructFieldIndex(allowStructFieldIndex));
        if (skipProcessor)
            builder.processorsToSkip().add(StructFieldIndexing.class);
        builder.addSchema(SCHEMA);
        builder.build(true);
        return builder.getSchema();
    }

    private static void assertScript(String expected, Schema schema, String fieldName) {
        assertEquals(expected, schema.getConcreteField(fieldName).getStructFieldIndexingScript().toString(), fieldName);
    }

    private static void assertEmptyScript(Schema schema, String fieldName) {
        assertTrue(schema.getConcreteField(fieldName).getStructFieldIndexingScript().isEmpty(), fieldName);
    }

}
