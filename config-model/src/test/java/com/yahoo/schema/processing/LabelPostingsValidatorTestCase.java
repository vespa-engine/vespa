// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static com.yahoo.schema.ApplicationBuilder.createFromString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class LabelPostingsValidatorTestCase {

    private static String schema(String rankSetting) {
        return joinLines("schema test {",
                "  document test {",
                "    field foo type tensor(x{}) {",
                "      indexing: attribute",
                "      attribute {",
                "        fast-rank",
                "        label-postings",
                "      }",
                "      " + rankSetting,
                "    }",
                "  }",
                "}");
    }

    @Test
    void label_postings_is_supported_without_rank_filter() throws ParseException {
        createFromString(schema(""));
    }

    @Test
    void label_postings_is_not_supported_with_rank_filter() throws ParseException {
        assertNotSupported(schema("rank: filter"));
    }

    private static void assertNotSupported(String sd) throws ParseException {
        try {
            createFromString(sd);
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals("For schema 'test', field 'foo': The 'label-postings' attribute setting is not supported together with 'rank: filter'",
                    e.getMessage());
        }
    }

}
