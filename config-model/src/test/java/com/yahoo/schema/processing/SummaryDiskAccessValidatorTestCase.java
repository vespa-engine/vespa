// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.derived.TestableDeployLogger;
import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SummaryDiskAccessValidatorTestCase {

    @Test
    void logs_warning_when_accessing_field_that_needs_disk_access() throws ParseException {
        var sd = joinLines(
                "schema test {",
                "  document test {",
                "    field str_map type map<string, string> {",
                "      indexing: summary",
                "      # Not all struct fields are attributes -> needs disk access",
                "      struct-field key { indexing: attribute }",
                "    }",
                "  }",
                "  document-summary my_sum {",
                "    summary str_map type map<string, string> { source: str_map }",
                "  }",
                "}");

        var logger = new TestableDeployLogger();
        ApplicationBuilder.createFromString(sd, logger);
        assertEquals(1, logger.warnings.size());
        assertThat(logger.warnings.get(0),
                containsString("In schema 'test', document summary 'my_sum': " +
                        "Fields [str_map] references non-attribute fields: Using this summary will cause disk accesses"));
    }

    @Test
    void does_not_log_warning_when_accessing_imported_map_field() throws ParseException {
        var parent = joinLines(
                "schema parent {",
                "  document parent {",
                "    field str_map type map<string, string> {",
                "      indexing: summary",
                "      # All struct fields must be attributes in order to import this fields",
                "      struct-field key { indexing: attribute }",
                "      struct-field value { indexing: attribute }",
                "    }",
                "  }",
                "}");

        var child = joinLines(
                "schema child {",
                "  document child {",
                "    field ref type reference<parent> { indexing: attribute }",
                "  }",
                "  import field ref.str_map as ref_str_map {}",
                "  document-summary my_sum {",
                "    summary ref_str_map type map<string, string> { source: ref_str_map }",
                "  }",
                "}");
        var logger = new TestableDeployLogger();
        ApplicationBuilder.createFromStrings(logger, child, parent);
        assertEquals(0, logger.warnings.size());
    }

}
