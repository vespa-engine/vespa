// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests that indexing statements are parsed correctly.
 *
 * @author frodelu
 */
public class IndexingParsingTestCase extends AbstractSchemaTestCase {

    @Test
    void requireThatIndexingExpressionsCanBeParsed() throws Exception {
        assertNotNull(ApplicationBuilder.buildFromFile("src/test/examples/indexing.sd"));
    }

    @Test
    void requireThatParseExceptionPositionIsCorrect() throws Exception {
        try {
            ApplicationBuilder.buildFromFile("src/test/examples/indexing_invalid_expression.sd");
        } catch (ParseException e) {
            if (!e.getMessage().contains("at line 5, column 57.")) {
                throw e;
            }
        }
    }

}
