// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.vespa.config.search.IndexschemaConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests deriving of struct fields with 'indexing: index' in array of struct and map fields.
 *
 * @author arnej
 */
public class StructFieldIndexTestCase extends AbstractExportingTestCase {

    @Test
    void testArrayOfStructIndex() throws IOException, ParseException {
        assertCorrectDeriving("array_of_struct_index", new TestProperties().allowStructFieldIndex("allow_alone"));
    }

    /** Struct fields do what their parent does, in addition to what their own indexing statements say. */
    @Test
    void testStructFieldOverridesInIndexedMode() throws IOException, ParseException {
        assertCorrectDeriving("struct_field_override", "struct_field_override",
                              new TestProperties().allowStructFieldIndex("allow_alone"), SchemaInfo.IndexMode.INDEX);
    }

    @Test
    void testStructFieldOverridesInStreamingMode() throws IOException, ParseException {
        assertCorrectDeriving("struct_field_override", "struct_field_override_streaming",
                              new TestProperties().allowStructFieldIndex("allow_alone"), SchemaInfo.IndexMode.STREAMING);
    }

    @Test
    void testStructFieldIndexingStatementsAreAddedToInheritedOnes() throws ParseException {
        var builder = new ApplicationBuilder(new TestProperties().allowStructFieldIndex("allow_alone"));
        builder.addSchema(joinLines("schema test {",
                                    "  document test {",
                                    "    struct s {",
                                    "      field a type string {}",
                                    "      field b type string {}",
                                    "    }",
                                    "    field f1 type array<s> {",
                                    "      indexing: summary",
                                    "      struct-field a { indexing: index }",
                                    "    }",
                                    "    field f2 type array<s> {",
                                    "      indexing: index | summary",
                                    "      struct-field a { indexing: attribute }",
                                    "    }",
                                    "    field f3 type array<s> {",
                                    "      struct-field a { indexing: index }",
                                    "    }",
                                    "  }",
                                    "}"));
        builder.build(true);
        var schema = builder.getSchema();

        // Processing the parent's script does not change the scripts of its struct fields
        assertTrue(schema.getConcreteField("f1").doesSummarying());
        assertFalse(schema.getConcreteField("f1").doesIndexing());
        assertAspects(schema.getConcreteField("f1").getStructField("a"), true, true, false);
        assertAspects(schema.getConcreteField("f1").getStructField("b"), false, true, false);

        assertAspects(schema.getConcreteField("f2").getStructField("a"), true, true, true);
        assertAspects(schema.getConcreteField("f2").getStructField("b"), true, true, false);

        // An empty script is not inherited
        assertAspects(schema.getConcreteField("f3").getStructField("a"), true, false, false);
        assertAspects(schema.getConcreteField("f3").getStructField("b"), false, false, false);
    }

    private static void assertAspects(SDField field, boolean indexing, boolean summarying, boolean attributing) {
        assertEquals(indexing, field.doesIndexing(), field.getName() + " does indexing");
        assertEquals(summarying, field.doesSummarying(), field.getName() + " does summarying");
        assertEquals(attributing, field.doesAttributing(), field.getName() + " does attributing");
    }

    @Test
    void testStructFieldIndexesDependOnFeatureFlag() throws ParseException {
        assertStructFieldIndexes("ignore", List.of());
        assertStructFieldIndexes("disallow", List.of());
        assertStructFieldIndexes("allow_alone", List.of("f1.a"));
        assertStructFieldIndexes("allow", List.of("f1.a", "f1.b"));
    }

    private static void assertStructFieldIndexes(String flagValue, List<String> expectedIndexes) throws ParseException {
        var builder = new ApplicationBuilder(new TestProperties().allowStructFieldIndex(flagValue));
        builder.addSchema(joinLines("schema test {",
                                    "  document test {",
                                    "    struct s {",
                                    "      field a type string {}",
                                    "      field b type string {}",
                                    "    }",
                                    "    field f1 type array<s> {",
                                    "      struct-field a { indexing: index }",
                                    "      struct-field b { indexing: index | attribute }",
                                    "    }",
                                    "  }",
                                    "}"));
        builder.build(true);
        var config = new IndexschemaConfig.Builder();
        new IndexSchema(builder.getSchema()).getConfig(config);
        assertEquals(expectedIndexes,
                     config.build().indexfield().stream().map(IndexschemaConfig.Indexfield::name).toList(),
                     "struct field indexes with allow-struct-field-index=" + flagValue);
    }

}
