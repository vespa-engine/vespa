// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.vespa.config.search.IndexschemaConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
