// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.schema.derived.DerivedConfiguration;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.yolean.Exceptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author bratseth
 */
public class IncorrectRankingExpressionFileRefTestCase extends AbstractSchemaTestCase {

    @Test
    void testIncorrectRef() throws IOException, ParseException {
        try {
            RankProfileRegistry registry = new RankProfileRegistry();
            Schema schema = ApplicationBuilder.buildFromFile("src/test/examples/incorrectrankingexpressionfileref.sd",
                    registry,
                    new QueryProfileRegistry());
            new DerivedConfiguration(schema, registry); // cause rank profile parsing
            fail("parsing should have failed");
        } catch (IllegalArgumentException e) {
            String message = Exceptions.toMessageString(e);
            assertTrue(message.contains("Could not read ranking expression file"));
            assertTrue(message.contains("wrongending.expr.expression"));
        }
    }

}
