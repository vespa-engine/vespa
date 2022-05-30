// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.schema.parser.ParseException;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

/**
 * Tests that indexing statements are parsed correctly.
 *
 * @author frodelu
 */
public class IndexingParsingTestCase extends AbstractSchemaTestCase {

    @Test
    public void requireThatIndexingExpressionsCanBeParsed() throws Exception {
        assertNotNull(ApplicationBuilder.buildFromFile("src/test/examples/indexing.sd"));
    }

    @Test
    public void requireThatParseExceptionPositionIsCorrect() throws Exception {
        try {
            ApplicationBuilder.buildFromFile("src/test/examples/indexing_invalid_expression.sd");
        } catch (ParseException e) {
            if (!e.getMessage().contains("at line 5, column 57.")) {
                throw e;
            }
        }
    }

}
