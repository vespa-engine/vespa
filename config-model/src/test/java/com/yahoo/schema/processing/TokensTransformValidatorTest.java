// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.Schema;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.vespa.documentmodel.SummaryTransform;
import org.junit.jupiter.api.Test;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class TokensTransformValidatorTest {
    private void buildSchema(String fieldType) throws ParseException {
        String sd = joinLines(
                "search test {",
                "  document test {",
                "      field f type " + fieldType + " {",
                "          indexing: index | summary",
                "          summary: tokens",
                "      }",
                "  }",
                "}"
        );
        Schema schema = ApplicationBuilder.createFromString(sd).getSchema();
    }

    void buildSchemaShouldFail(String fieldType, String expFail) throws ParseException {
        try {
            buildSchema(fieldType);
            fail("expected IllegalArgumentException with message '" + expFail + "'");
        } catch (IllegalArgumentException e) {
            assertEquals(expFail, e.getMessage());
        }
    }

    @Test
    void testTokensTransformWithPlainString() throws ParseException {
        buildSchema("string");
    }

    @Test
    void testTokensTransformWithArrayOfString() throws ParseException {
        buildSchema("array<string>");
    }

    @Test
    void testTokensTransformWithWeightedSetOfString() throws ParseException {
        buildSchema("weightedset<string>");
    }

    @Test
    void testTokensTransformWithWeightedSetOfInteger() throws ParseException {
        buildSchemaShouldFail("weightedset<int>", "For schema 'test', document-summary 'default'" +
                ", summary field 'f', source field 'f', source field type 'WeightedSet<int>'" +
                ": transform 'tokens' is only allowed for fields of type string, array<string> or weightedset<string>");
    }
}
